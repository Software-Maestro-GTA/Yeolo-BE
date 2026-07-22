package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseQueryServiceTest {

    /** 코스 영속 포트 fake: 미리 넣어둔 코스를 소유자 필터·식별자로 조회한다. */
    private static final class FakeCourseRepository implements CourseRepository {
        private final List<SavedCourse> store = new ArrayList<>();

        @Override
        public UUID save(Course course) {
            throw new UnsupportedOperationException("조회 테스트에서는 저장을 사용하지 않는다.");
        }

        @Override
        public List<SavedCourse> findByUserIdLatestFirst(UUID userId) {
            return store.stream().filter(c -> c.userId().equals(userId)).toList();
        }

        @Override
        public Optional<SavedCourse> findById(UUID courseId) {
            return store.stream().filter(c -> c.courseId().equals(courseId)).findFirst();
        }
    }

    private final FakeCourseRepository courses = new FakeCourseRepository();

    private CourseQueryService service() {
        return new CourseQueryService(courses);
    }

    private SavedCourse course(UUID courseId, UUID userId, String title, String itineraryJson) {
        return new SavedCourse(courseId, userId, title, "대한민국", "제주",
                LocalDate.of(2026, 8, 1), 3, List.of("힐링"), "이유", itineraryJson, Instant.now());
    }

    @Test
    void 내_코스만_요약_목록으로_반환한다() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        courses.store.add(course(UUID.randomUUID(), me, "내 코스 A", "{\"days\":[]}"));
        courses.store.add(course(UUID.randomUUID(), other, "남의 코스", "{\"days\":[]}"));
        courses.store.add(course(UUID.randomUUID(), me, "내 코스 B", "{\"days\":[]}"));

        CourseListResponse response = service().getMyCourses(me);

        assertThat(response.courses()).extracting(CourseListResponse.CourseSummary::title)
                .containsExactly("내 코스 A", "내 코스 B");
    }

    @Test
    void 코스가_없으면_빈_목록을_반환한다() {
        assertThat(service().getMyCourses(UUID.randomUUID()).courses()).isEmpty();
    }

    @Test
    void 소유자면_상세를_itinerary_노드로_반환한다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "내 코스", "{\"days\":[{\"day\":1,\"stops\":[]}]}"));

        CourseDetailResponse response = service().getCourse(me, courseId);

        assertThat(response.course().courseId()).isEqualTo(courseId.toString());
        assertThat(response.course().userId()).isEqualTo(me.toString());
        assertThat(response.course().itinerary().get("days")).hasSize(1);
    }

    @Test
    void 코스가_없으면_404() {
        assertThatThrownBy(() -> service().getCourse(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 타인_코스를_조회하면_403() {
        UUID owner = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, owner, "남의 코스", "{\"days\":[]}"));

        assertThatThrownBy(() -> service().getCourse(UUID.randomUUID(), courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_ACCESS_DENIED);
    }
}
