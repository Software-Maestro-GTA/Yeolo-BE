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
import com.soma.yeolo.global.sse.SseHeartbeat;
import com.soma.yeolo.global.sse.SseStream;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 지역/날짜/예산 조건 기반 코스 생성 오케스트레이션 (API-FB-4 / FUN-6).
 *
 * <p>조건 정규화 → 성향 프로필 로딩(DOM-1) → AI 코스 생성 호출(API-BA-1) → 결과 저장(DOM-2) 순으로
 * 진행하며, 각 단계 상태를 {@link SseEmitter}로 스트리밍한다. 이벤트 step명은 명세 그대로 사용한다.
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

    private final TasteProfileRepository tasteProfileRepository;
    private final AiCourseClient aiCourseClient;
    private final ItineraryPlaceNormalizer itineraryPlaceNormalizer;
    private final CourseAssembler courseAssembler;
    private final CourseRepository courseRepository;
    private final SseHeartbeat heartbeat;

    /** 정규화 → 성향 로딩 → AI 생성 → 저장 파이프라인을 실행하고 진행 상황을 SSE로 중계한다. */
    public void createAndStream(UUID userId, CourseCreationRequest request, SseEmitter emitter) {
        SseStream stream = new SseStream(emitter);
        // 성향 로딩과 AI 생성(수십 초) 모두 응답 바이트가 나가지 않는 구간이다.
        // 프록시가 연결을 idle로 보고 끊지 않도록 전 구간 heartbeat를 유지한다.
        try (SseHeartbeat.Handle beat = heartbeat.start(stream)) {
            TripCondition condition = normalize(request);

            stream.send(EVENT_PROGRESS,
                    new Progress("LOADING_TASTE_PROFILE", "사용자 성향 프로필을 불러오는 중입니다."));
            SavedTasteProfile profile = tasteProfileRepository.findLatestByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASTE_PROFILE_NOT_FOUND));

            stream.send(EVENT_PROGRESS,
                    new Progress("GENERATING_COURSE", "개인 맞춤형 여행 코스를 생성 중입니다."));
            JsonNode courseNode = aiCourseClient.generateCourse(
                    AiCourseGenerationRequest.of(userId, parseProfile(profile.profileJson()), condition));

            // 클라이언트가 이미 떠났어도 생성은 끝났으므로 저장은 진행한다(목록 조회로 확인 가능).
            // AI는 장소명만 주므로, 저장 전에 각 방문지를 내부 장소(placeId·좌표)로 정규화한다(DOM-3).
            itineraryPlaceNormalizer.normalize(courseNode, condition);
            Course course = courseAssembler.toDomain(userId, courseNode);
            UUID courseId = courseRepository.save(course);

            if (!stream.send(EVENT_COMPLETE, ApiResponse.success("여행 코스 생성 성공",
                    new CompleteData(courseId.toString())))) {
                log.warn("코스 생성 완료 이벤트를 전달하지 못했다(클라이언트 이탈). userId={}, courseId={}",
                        userId, courseId);
            }
            stream.complete();
        } catch (BusinessException e) {
            log.warn("Course creation failed: {}", e.getErrorCode());
            completeWithError(stream, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error during course creation", e);
            completeWithError(stream, ErrorCode.INTERNAL_ERROR);
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

    /**
     * 진행 중 오류를 명세 봉투로 담은 {@code error} 이벤트로 전송하고 스트림을 종료한다.
     * 이미 끊긴 스트림이면 {@link SseStream}이 조용히 무시한다 — 여기서 예외를 다시 던지면
     * 서블릿으로 재디스패치되어 전역 핸들러가 500을 찍는다.
     */
    private void completeWithError(SseStream stream, ErrorCode code) {
        stream.send(EVENT_ERROR, ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
        stream.complete();
    }
}
