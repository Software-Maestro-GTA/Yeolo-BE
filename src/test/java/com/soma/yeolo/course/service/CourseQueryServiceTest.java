package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.dto.Itinerary;
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

        @Override
        public boolean existsByUserId(UUID userId) {
            return store.stream().anyMatch(c -> c.userId().equals(userId));
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
        assertThat(response.course().itinerary().days()).hasSize(1);
    }

    @Test
    void 상세의_stop에_내부_placeId와_좌표를_담는다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "내 코스", """
                {"days":[{"day":1,"stops":[{"sequence":1,"placeId":"%s","placeName":"성산일출봉",
                  "latitude":33.4581,"longitude":126.9425}]}]}
                """.formatted(placeId)));

        Itinerary.Stop stop = service().getCourse(me, courseId)
                .course().itinerary().days().get(0).stops().get(0);

        assertThat(stop.placeId()).isEqualTo(placeId);
        assertThat(stop.latitude()).isEqualTo(33.4581);
        assertThat(stop.longitude()).isEqualTo(126.9425);
    }

    /**
     * 장소 정규화 이전에 저장된 코스에는 AI가 넣은 외부 식별자가 남아 있을 수 있다. 그 값은 응답에
     * 실리지 않아야 하고(DOM-3: Google Place ID 비노출), 조회를 깨뜨려서도 안 된다.
     */
    @Test
    void 내부_placeId가_아닌_값은_응답에서_제외하고_조회는_성공한다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "예전 코스",
                "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,\"placeId\":\"ChIJ_GOOGLE_ID\"}]}]}"));

        CourseDetailResponse response = service().getCourse(me, courseId);

        assertThat(response.course().itinerary().days().get(0).stops().get(0).placeId()).isNull();
        assertThat(response.toString()).doesNotContain("ChIJ_GOOGLE_ID");
    }

    /**
     * placeId 자리에 객체·배열이 들어 있어도 그 값 전체를 건너뛰어야 한다. 열림 토큰만 남기고 넘어가면
     * 뒤이은 필드를 값 안쪽부터 읽어 stop이 쪼개지는 등 조용히 깨진 응답이 나간다.
     */
    @Test
    void placeId가_객체나_배열이어도_나머지_stop_필드를_망가뜨리지_않는다() {
        UUID me = UUID.randomUUID();
        UUID objectCourse = UUID.randomUUID();
        UUID arrayCourse = UUID.randomUUID();
        courses.store.add(course(objectCourse, me, "객체 placeId",
                "{\"days\":[{\"day\":1,\"stops\":[{\"placeId\":{\"id\":\"ChIJ_GOOGLE_ID\"},"
                        + "\"placeName\":\"성산일출봉\"}]}]}"));
        courses.store.add(course(arrayCourse, me, "배열 placeId",
                "{\"days\":[{\"day\":1,\"stops\":[{\"placeId\":[\"ChIJ_GOOGLE_ID\"],"
                        + "\"placeName\":\"성산일출봉\"}]}]}"));

        for (UUID courseId : List.of(objectCourse, arrayCourse)) {
            List<Itinerary.Stop> stops =
                    service().getCourse(me, courseId).course().itinerary().days().get(0).stops();

            assertThat(stops).hasSize(1);
            assertThat(stops.get(0).placeId()).isNull();
            assertThat(stops.get(0).placeName()).isEqualTo("성산일출봉");
        }
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
