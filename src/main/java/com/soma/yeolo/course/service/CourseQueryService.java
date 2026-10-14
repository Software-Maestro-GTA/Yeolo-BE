package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseDetailResponse.CostConversion;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.course.service.port.CourseSharing;
import com.soma.yeolo.course.service.port.ExchangeRateProvider;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.course.client.ExchangeRateProperties;
import com.soma.yeolo.location.service.CountryCurrencyReader;
import com.soma.yeolo.location.service.CountryCurrencyReader.CountryCurrency;
import com.soma.yeolo.location.service.LocationImageReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 조회·관리 (API-COURSE-3 목록 / API-COURSE-2 상세 / API-COURSE-4 삭제 / FUN-9).
 *
 * <p>목록은 사용자의 코스를 최신순 요약으로 반환하고, 상세는 접근 권한을 검증한 뒤 일자·방문지
 * 전체를 포함해 반환한다. 코스 없음은 404, 권한 없는 접근은 403으로 노출한다(전역 핸들러).
 *
 * <p><b>공유받은 코스도 내 코스처럼 다룬다</b> (DOM-6). 친구 초대를 수락하면 목록·상세에서 함께
 * 보이고, 삭제 요청은 소유자면 원본 삭제, 공유받은 사용자면 내 목록에서만 제거된다. 목록에서
 * 소유 코스와 공유 코스를 구분해 표시하지는 않는다 — DOM-6이 이번 스프린트엔 구분하지 않아도
 * 된다고 명시하며, 명세(API-COURSE-3)에 구분용 필드가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseQueryService implements RecentCourseReader {

    // 저장된 원본 itinerary JSON에 명세에 없는 필드가 섞일 수 있으므로 미지 필드는 무시하고 역직렬화한다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CourseRepository courseRepository;
    private final CourseSharing courseSharing;
    private final LocationImageReader locationImageReader;
    private final CountryCurrencyReader countryCurrencyReader;
    private final ExchangeRateProvider exchangeRateProvider;
    private final ExchangeRateProperties exchangeRateProperties;

    /**
     * 사용자의 코스를 최신순 요약 목록으로 반환한다. 없으면 빈 목록. (API-COURSE-3)
     *
     * <p>직접 만든 코스와 공유받아 수락한 코스를 합쳐 생성 시각 기준 최신순으로 정렬한다. 공유 코스의
     * 정렬 기준은 수락 시각이 아니라 <b>원본 코스의 생성 시각</b>이다 — 응답의 {@code createdAt}이
     * 그 값이므로, 다른 기준으로 정렬하면 목록이 자기 필드와 어긋난 순서로 보인다.
     */
    @Transactional(readOnly = true)
    public CourseListResponse getMyCourses(UUID userId) {
        List<SavedCourse> owned = courseRepository.findByUserIdLatestFirst(userId);
        List<SavedCourse> shared =
                courseRepository.findAllByIdsLatestFirst(courseSharing.findSharedCourseIds(userId));

        Set<UUID> seen = new HashSet<>();
        List<SavedCourse> merged = new ArrayList<>(owned.size() + shared.size());
        for (SavedCourse course : owned) {
            if (seen.add(course.courseId())) {
                merged.add(course);
            }
        }
        for (SavedCourse course : shared) {
            if (seen.add(course.courseId())) {
                merged.add(course);
            }
        }
        merged.sort(Comparator.comparing(SavedCourse::createdAt).reversed());
        // 같은 목적지의 코스가 여럿이면(같은 도시를 여러 번 생성) 사진 조회(최대 6 질의)를 한 번만 한다.
        // 목적지가 전부 다르면 목적지 수만큼 조회가 는다 — 목록이 수십 건 규모라 받아들이고, 커지면
        // (국가, 도시) 집합을 한 번에 묻는 일괄 조회나 저장 시점 고정으로 바꾼다.
        Map<List<String>, Optional<String>> cityImages = new HashMap<>();
        return CourseListResponse.from(merged, course -> coverImageUrl(course, cityImages));
    }

    /**
     * 목록의 대표 이미지(API-COURSE-3 {@code coverImageUrl}). AI 계약(API-AI-2)에서 대표 이미지가
     * 빠져 새 코스는 저장값이 없으므로 목적지 도시 사진(#97 데이터셋)으로 채운다. 그것도 없으면 null.
     */
    private String coverImageUrl(SavedCourse course, Map<List<String>, Optional<String>> cityImages) {
        if (course.coverImageUrl() != null) {
            return course.coverImageUrl();
        }
        List<String> destination = List.of(course.destinationCountry(), course.destinationCity());
        return cityImages.computeIfAbsent(destination, key ->
                locationImageReader.findCityImageUrl(course.destinationCountry(), course.destinationCity()))
                .orElse(null);
    }

    /**
     * 로그인 응답에 실을 최근 코스 식별자를 반환한다. (API-AUTH-1 / API-AUTH-2)
     *
     * <p>목록({@link #getMyCourses})의 맨 위 항목과 같은 코스를 가리킨다 — 소유·공유를 함께 보고
     * 원본 코스 생성 시각 기준 최신순이라는 규칙이 같다. 다만 목록을 조립해 첫 항목을 꺼내지는
     * 않는다(근거는 {@code CourseRepository.findRecentCourseId} 문서 참고).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findRecentCourseId(UUID userId) {
        return courseRepository.findRecentCourseId(userId, courseSharing.findSharedCourseIds(userId));
    }

    /**
     * 코스 상세를 반환한다. 코스가 없으면 404, 소유자도 공유받은 사용자도 아니면 403으로 응답한다.
     * (API-COURSE-2 / DOM-6 §권한 정책 — "공유받은 코스 상세 조회")
     */
    /**
     * 상세 조회. 트랜잭션을 걸지 않는다 — 환율 조회({@link #convertCost})가 외부 HTTP 호출이라 트랜잭션
     * 안에서 돌리면 제공처가 느릴 때 커넥션 풀의 연결을 수 초씩 붙들어 다른 API 까지 끌려 들어간다.
     * 코스·공유 조회는 각각 읽기 전용 모델을 돌려주는 단일 쿼리라 하나의 트랜잭션으로 묶을 이유가 없다.
     */
    public CourseDetailResponse getCourse(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId) && !courseSharing.isSharedWith(courseId, userId)) {
            throw new BusinessException(ErrorCode.COURSE_ACCESS_DENIED);
        }
        Itinerary itinerary = parseItinerary(course.itineraryJson());
        return CourseDetailResponse.from(course, itinerary, convertCost(course));
    }

    /**
     * 코스 총비용의 현지 통화 환산 (API-COURSE-2 {@code exRateIcon}·{@code totalCostExRate}, #100).
     *
     * <p>AI 는 {@code totalCost}를 목적지와 무관하게 원화({@code exchange-rate.source-currency})로 주므로,
     * 목적지 국가의 현지 통화(데이터셋 값)로 환산하고 그 통화 기호를 {@code exRateIcon}에 담는다 —
     * FE 가 "₩424,894 (¥50,000)" 처럼 나란히 보여준다. 국내 코스는 같은 통화라 환산값이 원래 금액이다.
     * 환산은 조회 시점에 한다(환율이 매일 바뀐다). 소수 통화 단위는 반올림해 정수로 내린다.
     */
    private CostConversion convertCost(SavedCourse course) {
        Optional<CountryCurrency> local = countryCurrencyReader.findCurrency(course.destinationCountry());
        if (local.isEmpty()) {
            return CostConversion.NONE;
        }
        String icon = local.get().symbol();
        if (course.totalCost() == null) {
            return new CostConversion(null, icon);
        }
        try {
            Long converted = exchangeRateProvider.rate(exchangeRateProperties.sourceCurrency(), local.get().code())
                    .map(rate -> BigDecimal.valueOf(course.totalCost()).multiply(rate)
                            .setScale(0, RoundingMode.HALF_UP).longValueExact())
                    .orElse(null);
            return new CostConversion(converted, icon);
        } catch (RuntimeException e) {
            // 환산은 참고값이다. 제공처·설정 문제로 상세 조회를 500 으로 만들지 않는다.
            log.warn("코스 비용 환산에 실패해 환산값 없이 응답한다. courseId={}", course.courseId(), e);
            return new CostConversion(null, icon);
        }
    }

    /**
     * 코스를 삭제한다. 코스가 없으면 404, 권한이 없으면 403으로 응답한다. (API-COURSE-4)
     *
     * <p>같은 엔드포인트지만 호출자에 따라 하는 일이 다르다 — <b>소유자는 원본을 삭제</b>하고,
     * <b>공유받은 사용자는 자기 목록에서만 제거</b>한다 (DOM-6 §보안 및 운영 정책: "공유받은
     * 사용자의 삭제 동작은 원본 삭제가 아니라 접근 권한 제거로 처리한다"). 어느 쪽도 아니면 403이다.
     *
     * <p>소유자가 원본을 지우면 그 코스의 공유(접근 권한·발급된 링크)도 함께 정리한다. 남겨 두면
     * 다른 사용자 목록에 열 수 없는 코스가 남고, 초대 링크도 계속 살아 있게 된다.
     *
     * <p>없는 코스와 권한 없는 코스를 다른 코드로 구분해 알려 준다 — 조회(API-COURSE-2)가 이미 같은
     * 방식이라 삭제만 감출 실익이 없고, 명세가 404·403을 모두 정의하고 있다.
     */
    @Transactional
    public void deleteCourse(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId)) {
            if (!courseSharing.removeShare(courseId, userId)) {
                throw new BusinessException(ErrorCode.COURSE_DELETE_ACCESS_DENIED);
            }
            return;
        }
        courseSharing.revokeAllForCourse(courseId);
        courseRepository.deleteById(courseId);
    }

    private Itinerary parseItinerary(String itineraryJson) {
        try {
            return OBJECT_MAPPER.readValue(itineraryJson, Itinerary.class);
        } catch (Exception e) {
            // 저장된 itinerary JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
