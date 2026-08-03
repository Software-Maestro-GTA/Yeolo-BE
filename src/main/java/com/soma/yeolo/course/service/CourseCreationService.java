package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.client.AiCourseClient;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;
import com.soma.yeolo.course.domain.BudgetType;
import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.course.dto.CourseCreationEvents.CompleteData;
import com.soma.yeolo.course.dto.CourseCreationEvents.Progress;
import com.soma.yeolo.course.dto.CourseCreationRequest;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import com.soma.yeolo.user.service.port.UserPreferenceReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 지역/날짜/예산 조건 기반 코스 생성 오케스트레이션 (API-COURSE-1 / FUN-6).
 *
 * <p>조건 정규화 → 선호(MBTI)·성향 프로필 로딩 → AI 코스 생성 호출(API-AI-2) → 장소 정규화(내부
 * placeId·좌표) → 결과 저장(DOM-3) 순으로 진행하며, 각 단계 상태를 {@link SseEmitter}로 스트리밍한다.
 * MBTI 또는 취향 프로필 중 하나 이상만 있으면 생성하며, 이벤트 step명은 명세 그대로 사용한다.
 * 이 메서드는 비동기 워커 스레드에서 실행되어 전체 SSE 수명주기를 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseCreationService {

    private static final String EVENT_PROGRESS = "progress";
    private static final String EVENT_COMPLETE = "complete";
    private static final String EVENT_ERROR = "error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserPreferenceReader userPreferenceReader;
    private final TasteProfileRepository tasteProfileRepository;
    private final AiCourseClient aiCourseClient;
    private final ItineraryPlaceNormalizer itineraryPlaceNormalizer;
    private final CourseAssembler courseAssembler;
    private final CourseRepository courseRepository;

    /** 정규화 → 선호(MBTI)·성향 로딩 → AI 생성 → 장소 정규화 → 저장 파이프라인을 실행하고 진행 상황을 SSE로 중계한다. */
    public void createAndStream(UUID userId, CourseCreationRequest request, SseEmitter emitter) {
        try {
            TripCondition condition = normalize(request);

            emit(emitter, EVENT_PROGRESS,
                    new Progress("LOADING_USER_PREFERENCE", "사용자 정보를 불러오는 중입니다."));
            // MBTI 또는 취향 프로필 중 하나 이상이 있으면 코스를 생성한다. (DOM-3 코스 생성 기준)
            // 둘 다 없으면 개인화 근거가 전혀 없으므로 404로 노출한다. (API-COURSE-1 §4: 404 성향 정보 없음)
            Optional<String> mbti = userPreferenceReader.findMbtiByUserId(userId);
            Optional<SavedTasteProfile> profile = tasteProfileRepository.findLatestByUserId(userId);
            if (mbti.isEmpty() && profile.isEmpty()) {
                throw new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND);
            }
            JsonNode tasteProfile = profile.map(p -> parseProfile(p.profileJson())).orElse(null);

            emit(emitter, EVENT_PROGRESS,
                    new Progress("GENERATING_COURSE", "개인 맞춤형 여행 코스를 생성 중입니다."));
            JsonNode courseNode = aiCourseClient.generateCourse(
                    AiCourseGenerationRequest.of(userId, mbti.orElse(null), tasteProfile, condition));

            // AI 응답은 placeName·category 중심 → BE가 내부 placeId·좌표로 정규화해 저장한다. (DOM-3)
            JsonNode normalizedCourse = itineraryPlaceNormalizer.normalize(courseNode);
            Course course = courseAssembler.toDomain(userId, normalizedCourse);
            UUID courseId = courseRepository.save(course);

            emit(emitter, EVENT_COMPLETE, ApiResponse.success("여행 코스 생성 성공",
                    new CompleteData(courseId.toString())));
            emitter.complete();
        } catch (BusinessException e) {
            log.warn("Course creation failed: {}", e.getErrorCode());
            completeWithError(emitter, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error during course creation", e);
            completeWithError(emitter, ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 요청 문자열 조건을 검증·파싱된 도메인 조건으로 정규화한다. (FUN-6) */
    private TripCondition normalize(CourseCreationRequest request) {
        try {
            return new TripCondition(
                    request.destinationCountry(),
                    request.destinationCity(),
                    LocalDate.parse(request.startDate()),
                    request.totalDays(),
                    BudgetType.fromValue(request.budgetType())
            );
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_CONDITION, e);
        }
    }

    private JsonNode parseProfile(String profileJson) {
        try {
            return OBJECT_MAPPER.readTree(profileJson);
        } catch (Exception e) {
            // 저장된 성향 JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }

    private void emit(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload, MediaType.APPLICATION_JSON));
    }

    /** 진행 중 오류를 명세 봉투로 담은 {@code error} 이벤트로 전송하고 스트림을 종료한다. */
    private void completeWithError(SseEmitter emitter, ErrorCode code) {
        try {
            emit(emitter, EVENT_ERROR, ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
            emitter.complete();
        } catch (Exception sendFailure) {
            log.debug("Failed to send SSE error event: {}", sendFailure.getMessage());
            emitter.completeWithError(sendFailure);
        }
    }
}
