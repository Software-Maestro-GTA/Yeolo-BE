package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.client.AiCourseClient;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;
import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.dto.CourseCreationRequest;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@ExtendWith(MockitoExtension.class)
class CourseCreationServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SseEmitter emitter;

    /** 성향 프로필 영속 포트 fake: 미리 정해둔 최신 프로필을 돌려준다. */
    private static final class FakeTasteProfileRepository implements TasteProfileRepository {
        private Optional<SavedTasteProfile> latest = Optional.empty();

        @Override
        public UUID save(TasteProfile profile) {
            throw new UnsupportedOperationException("코스 생성 테스트에서는 저장을 사용하지 않는다.");
        }

        @Override
        public Optional<SavedTasteProfile> findLatestByUserId(UUID userId) {
            return latest;
        }
    }

    /** 코스 영속 포트 fake: 저장된 도메인을 기록하고 미리 정해둔 id를 돌려준다. */
    private static final class FakeCourseRepository implements CourseRepository {
        private final UUID assignedId = UUID.randomUUID();
        private final List<Course> saved = new ArrayList<>();

        @Override
        public UUID save(Course course) {
            saved.add(course);
            return assignedId;
        }

        @Override
        public java.util.List<com.soma.yeolo.course.domain.SavedCourse> findByUserIdLatestFirst(UUID userId) {
            throw new UnsupportedOperationException("코스 생성 테스트에서는 조회를 사용하지 않는다.");
        }

        @Override
        public java.util.Optional<com.soma.yeolo.course.domain.SavedCourse> findById(UUID courseId) {
            throw new UnsupportedOperationException("코스 생성 테스트에서는 조회를 사용하지 않는다.");
        }
    }

    /** AI 코스 생성 포트 fake: 정해둔 노드를 반환하거나, 설정 시 예외를 던진다. */
    private static final class FakeAiCourseClient implements AiCourseClient {
        private JsonNode result;
        private BusinessException failure;

        @Override
        public JsonNode generateCourse(AiCourseGenerationRequest request) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private final FakeTasteProfileRepository tasteProfiles = new FakeTasteProfileRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeAiCourseClient aiClient = new FakeAiCourseClient();

    private CourseCreationService service() {
        return new CourseCreationService(tasteProfiles, aiClient, new CourseAssembler(), courses);
    }

    private CourseCreationRequest request() {
        return new CourseCreationRequest("대한민국", "제주", "2026-08-01", 3, "cost_effective");
    }

    private SavedTasteProfile savedProfile(UUID userId) {
        return new SavedTasteProfile(UUID.randomUUID(), userId, SourceType.BEHAVIOR, Instant.now(),
                "{\"sourceType\":\"behavior\"}");
    }

    private JsonNode courseNode() throws Exception {
        return MAPPER.readTree("""
                {
                  "title": "2박 3일 제주 힐링 코스",
                  "destinationCountry": "대한민국",
                  "destinationCity": "제주",
                  "startDate": "2026-08-01",
                  "totalDays": 3,
                  "tags": ["힐링"],
                  "recommendationReason": "여유로운 일정",
                  "itinerary": {"days": [{"day": 1, "stops": []}]}
                }
                """);
    }

    @Test
    void 성공하면_두_progress와_complete를_보내고_코스를_저장한다() throws Exception {
        UUID userId = UUID.randomUUID();
        tasteProfiles.latest = Optional.of(savedProfile(userId));
        aiClient.result = courseNode();

        service().createAndStream(userId, request(), emitter);

        // LOADING_TASTE_PROFILE + GENERATING_COURSE + complete = send 3회, 정상 종료
        verify(emitter, times(3)).send(any(SseEventBuilder.class));
        assertThat(courses.saved).hasSize(1);
        assertThat(courses.saved.getFirst().userId()).isEqualTo(userId);
        assertThat(courses.saved.getFirst().title()).isEqualTo("2박 3일 제주 힐링 코스");
        verify(emitter).complete();
    }

    @Test
    void 성향_프로필이_없으면_error를_보내고_AI를_호출하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        tasteProfiles.latest = Optional.empty();

        service().createAndStream(userId, request(), emitter);

        // LOADING progress(1) + error(1) = send 2회
        verify(emitter, times(2)).send(any(SseEventBuilder.class));
        assertThat(courses.saved).isEmpty();
        verify(emitter).complete();
    }

    @Test
    void AI_생성_실패시_error를_보내고_저장하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        tasteProfiles.latest = Optional.of(savedProfile(userId));
        aiClient.failure = new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);

        service().createAndStream(userId, request(), emitter);

        // LOADING(1) + GENERATING(2) + error(3) = send 3회
        verify(emitter, times(3)).send(any(SseEventBuilder.class));
        assertThat(courses.saved).isEmpty();
        verify(emitter).complete();
    }

    @Test
    void 잘못된_예산_조건이면_error를_보내고_성향을_조회하지_않는다() throws Exception {
        UUID userId = UUID.randomUUID();
        CourseCreationRequest invalid =
                new CourseCreationRequest("대한민국", "제주", "2026-08-01", 3, "premium");

        service().createAndStream(userId, invalid, emitter);

        // 정규화 단계에서 실패 → error(1)만 전송, 진행 이벤트 없음
        verify(emitter, times(1)).send(any(SseEventBuilder.class));
        assertThat(courses.saved).isEmpty();
        verify(emitter).complete();
    }
}
