package com.soma.yeolo.course.service;

import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.location.service.LocationImageReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 코스 대표 이미지({@code coverImageUrl}) 결정 규칙 — 코스 목록(API-COURSE-3)과 공유 링크 미리보기
 * (API-SHARE-2)가 {@link CourseCoverImageReader} 포트로 함께 쓴다.
 *
 * <p>AI 계약(API-AI-2, 2026-10 개정)에서 대표 이미지가 빠져 새 코스는 저장값이 없다. 그래서
 * <b>저장값 → 목적지 도시 사진(#97 데이터셋: 도시 → 수도 → 사진 있는 최다 인구 도시) → null</b>
 * 순으로 채운다. 두 API가 같은 코스에 다른 그림을 보여주지 않도록 규칙을 한 곳에 둔다.
 *
 * <p>조회 시점에 계산하고 저장하지 않는다 — 사진 URL 은 환경별 {@code LOCATION_IMAGE_BASE_URL}에 묶여
 * 있어 DB 에 고정하면 환경 간 이식이 깨지고, 데이터셋 갱신도 기존 코스에 반영되지 않는다(목록 #97·#98
 * 에서 내린 결정과 같다).
 *
 * <p>명세는 두 API 모두 {@code coverImageUrl}을 {@code string}으로만 적지만(nullable 표기 없음), 사진이
 * 없는 목적지에는 돌려줄 값이 없어 null 을 낸다 — API-COURSE-3 과 같은 BE 판단이다(docs/spec-index.md
 * §명세 모순과 BE 판단).
 */
@Component
@RequiredArgsConstructor
public class CourseCoverImageResolver implements CourseCoverImageReader {

    private final LocationImageReader locationImageReader;

    @Override
    public String resolve(SavedCourse course) {
        return resolve(course, this::cityImage);
    }

    /**
     * 같은 목적지의 코스가 여럿이면(같은 도시를 여러 번 생성) 사진 조회(최대 6 질의)를 한 번만 한다.
     * 목적지가 전부 다르면 목적지 수만큼 조회가 는다 — 목록이 수십 건 규모라 받아들이고, 커지면
     * (국가, 도시) 집합을 한 번에 묻는 일괄 조회로 바꾼다.
     */
    @Override
    public Function<SavedCourse, String> batch() {
        Map<List<String>, Optional<String>> cityImages = new HashMap<>();
        // 키는 null 을 허용하는 Arrays.asList — 목적지가 비어 있어도(엔티티는 not null 이지만) 목록 전체를 500 으로
        // 만들지 않고 "사진 없음"으로 떨어진다.
        Function<SavedCourse, Optional<String>> memoized = course -> cityImages.computeIfAbsent(
                Arrays.asList(course.destinationCountry(), course.destinationCity()), key -> cityImage(course));
        return course -> resolve(course, memoized);
    }

    /** 규칙의 단일 정의 — 저장값이 있으면 그것, 없으면 도시 사진, 그것도 없으면 null. */
    private static String resolve(SavedCourse course, Function<SavedCourse, Optional<String>> cityImage) {
        if (course.coverImageUrl() != null) {
            return course.coverImageUrl();
        }
        return cityImage.apply(course).orElse(null);
    }

    private Optional<String> cityImage(SavedCourse course) {
        return locationImageReader.findCityImageUrl(course.destinationCountry(), course.destinationCity());
    }
}
