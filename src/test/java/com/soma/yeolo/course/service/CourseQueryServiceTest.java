package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.course.service.port.CourseSharing;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            return latestFirst(store.stream().filter(c -> c.userId().equals(userId)).toList());
        }

        @Override
        public List<SavedCourse> findAllByIdsLatestFirst(Collection<UUID> courseIds) {
            return latestFirst(store.stream().filter(c -> courseIds.contains(c.courseId())).toList());
        }

        /** 실제 어댑터와 같은 정렬(최신 생성순)을 흉내낸다 — 저장 순서를 그대로 돌려주면 포트 계약과 어긋난다. */
        private List<SavedCourse> latestFirst(List<SavedCourse> found) {
            return found.stream()
                    .sorted(Comparator.comparing(SavedCourse::createdAt).reversed())
                    .toList();
        }

        @Override
        public Optional<UUID> findRecentCourseId(UUID userId, Collection<UUID> sharedCourseIds) {
            return latestFirst(store.stream()
                    .filter(c -> c.userId().equals(userId) || sharedCourseIds.contains(c.courseId()))
                    .toList()).stream().findFirst().map(SavedCourse::courseId);
        }

        @Override
        public Optional<SavedCourse> findById(UUID courseId) {
            return store.stream().filter(c -> c.courseId().equals(courseId)).findFirst();
        }

        @Override
        public void deleteById(UUID courseId) {
            store.removeIf(c -> c.courseId().equals(courseId));
        }

        @Override
        public boolean existsByUserId(UUID userId) {
            return store.stream().anyMatch(c -> c.userId().equals(userId));
        }
    }

    /** 코스 공유 포트 fake: 수락된 (코스, 사용자) 쌍을 인메모리로 들고 있는다. */
    private static final class FakeCourseSharing implements CourseSharing {
        private final Set<List<UUID>> accesses = new LinkedHashSet<>();
        private final List<UUID> revokedCourses = new ArrayList<>();

        void grant(UUID courseId, UUID userId) {
            accesses.add(List.of(courseId, userId));
        }

        @Override
        public List<UUID> findSharedCourseIds(UUID userId) {
            return accesses.stream().filter(pair -> pair.get(1).equals(userId))
                    .map(pair -> pair.get(0)).toList();
        }

        @Override
        public boolean isSharedWith(UUID courseId, UUID userId) {
            return accesses.contains(List.of(courseId, userId));
        }

        @Override
        public boolean removeShare(UUID courseId, UUID userId) {
            return accesses.remove(List.of(courseId, userId));
        }

        @Override
        public void revokeAllForCourse(UUID courseId) {
            accesses.removeIf(pair -> pair.get(0).equals(courseId));
            revokedCourses.add(courseId);
        }
    }

    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final FakeCourseSharing sharing = new FakeCourseSharing();

    private CourseQueryService service() {
        return new CourseQueryService(courses, sharing);
    }

    private SavedCourse course(UUID courseId, UUID userId, String title, String itineraryJson) {
        return course(courseId, userId, title, itineraryJson, Instant.now());
    }

    private SavedCourse course(UUID courseId, UUID userId, String title, String itineraryJson,
                               Instant createdAt) {
        return new SavedCourse(courseId, userId, title, "대한민국", "제주",
                "https://cdn.example.com/cover.jpg",
                LocalDate.of(2026, 8, 1), 3, List.of("힐링"), "이유", itineraryJson, createdAt);
    }

    @Test
    void 내_코스만_최신순_요약_목록으로_반환한다() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        courses.store.add(course(UUID.randomUUID(), me, "내 코스 A", "{\"days\":[]}", base));
        courses.store.add(course(UUID.randomUUID(), other, "남의 코스", "{\"days\":[]}", base));
        courses.store.add(course(UUID.randomUUID(), me, "내 코스 B", "{\"days\":[]}", base.plusSeconds(60)));

        CourseListResponse response = service().getMyCourses(me);

        assertThat(response.courses()).extracting(CourseListResponse.CourseSummary::title)
                .containsExactly("내 코스 B", "내 코스 A");
    }

    @Test
    void 코스가_없으면_빈_목록을_반환한다() {
        assertThat(service().getMyCourses(UUID.randomUUID()).courses()).isEmpty();
    }

    /** 목록에도 대표 이미지가 실린다 (API-COURSE-3 명세 개정). */
    @Test
    void 목록에_대표_이미지_URL을_담는다() {
        UUID me = UUID.randomUUID();
        courses.store.add(course(UUID.randomUUID(), me, "내 코스", "{\"days\":[]}"));

        assertThat(service().getMyCourses(me).courses())
                .extracting(CourseListResponse.CourseSummary::coverImageUrl)
                .containsExactly("https://cdn.example.com/cover.jpg");
    }

    // ===== 로그인 응답의 최근 코스 (API-AUTH-1 / API-AUTH-2) =====

    /** 목록 맨 위 항목과 같은 코스를 가리켜야 한다. */
    @Test
    void 최근_코스는_가장_최근에_만들어진_코스다() {
        UUID me = UUID.randomUUID();
        UUID newest = UUID.randomUUID();
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        courses.store.add(course(UUID.randomUUID(), me, "옛 코스", "{\"days\":[]}", base));
        courses.store.add(course(newest, me, "새 코스", "{\"days\":[]}", base.plusSeconds(60)));

        assertThat(service().findRecentCourseId(me)).contains(newest);
    }

    /** 목록이 소유·공유를 구분하지 않으므로(DOM-6) 최근 코스도 공유받은 코스를 함께 본다. */
    @Test
    void 공유받은_코스가_더_최근이면_그것을_최근_코스로_본다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        courses.store.add(course(UUID.randomUUID(), me, "내 옛 코스", "{\"days\":[]}", base));
        courses.store.add(course(sharedId, friend, "공유 코스", "{\"days\":[]}", base.plusSeconds(60)));
        sharing.grant(sharedId, me);

        assertThat(service().findRecentCourseId(me)).contains(sharedId);
    }

    @Test
    void 코스가_하나도_없으면_최근_코스는_빈_값이다() {
        assertThat(service().findRecentCourseId(UUID.randomUUID())).isEmpty();
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
    void 상세의_stop에_장소와_이동_정보를_명세_구조로_담는다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "내 코스", """
                {"days":[{"day":1,"stops":[{"sequence":1,"arrivalTime":"09:00","stayMinutes":90,
                  "memo":"일출","reason":"대표 명소",
                  "place":{"placeId":"%s","placeName":"성산일출봉","category":"nature",
                    "latitude":33.4581,"longitude":126.9425},
                  "transportToNext":{"type":"driving","distance":12.5,"minutes":40,
                    "cost":3000,"memo":"렌터카"}}]}]}
                """.formatted(placeId)));

        Itinerary.Stop stop = service().getCourse(me, courseId)
                .course().itinerary().days().get(0).stops().get(0);

        assertThat(stop.arrivalTime()).isEqualTo("09:00");
        assertThat(stop.place().placeId()).isEqualTo(placeId);
        assertThat(stop.place().placeName()).isEqualTo("성산일출봉");
        assertThat(stop.place().latitude()).isEqualTo(33.4581);
        assertThat(stop.place().longitude()).isEqualTo(126.9425);
        assertThat(stop.transportToNext().type()).isEqualTo("driving");
        assertThat(stop.transportToNext().distance()).isEqualTo(12.5);
        assertThat(stop.transportToNext().minutes()).isEqualTo(40);
        assertThat(stop.transportToNext().cost()).isEqualTo(3000);
        assertThat(stop.transportToNext().memo()).isEqualTo("렌터카");
    }

    /** 상세는 코스 요약도 명세대로 담는다 — 대표 이미지는 명세 개정으로 추가됐다. */
    @Test
    void 상세에_대표_이미지_URL을_담는다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "내 코스", "{\"days\":[]}"));

        assertThat(service().getCourse(me, courseId).course().coverImageUrl())
                .isEqualTo("https://cdn.example.com/cover.jpg");
    }

    /**
     * AI가 넣은 외부 식별자는 응답에 실리지 않아야 하고(DOM-3: Google Place ID 비노출), 조회를
     * 깨뜨려서도 안 된다. 장소 정규화에 실패한 코스가 이 경로를 탄다.
     */
    @Test
    void 내부_placeId가_아닌_값은_응답에서_제외하고_조회는_성공한다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "예전 코스",
                "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,"
                        + "\"place\":{\"placeId\":\"ChIJ_GOOGLE_ID\"}}]}]}"));

        CourseDetailResponse response = service().getCourse(me, courseId);

        assertThat(response.course().itinerary().days().get(0).stops().get(0).place().placeId()).isNull();
        assertThat(response.toString()).doesNotContain("ChIJ_GOOGLE_ID");
    }

    /**
     * placeId 자리에 객체·배열이 들어 있어도 그 값 전체를 건너뛰어야 한다. 열림 토큰만 남기고 넘어가면
     * 뒤이은 필드를 값 안쪽부터 읽어 장소가 쪼개지는 등 조용히 깨진 응답이 나간다.
     */
    @Test
    void placeId가_객체나_배열이어도_나머지_place_필드를_망가뜨리지_않는다() {
        UUID me = UUID.randomUUID();
        UUID objectCourse = UUID.randomUUID();
        UUID arrayCourse = UUID.randomUUID();
        courses.store.add(course(objectCourse, me, "객체 placeId",
                "{\"days\":[{\"day\":1,\"stops\":[{\"place\":{\"placeId\":{\"id\":\"ChIJ_GOOGLE_ID\"},"
                        + "\"placeName\":\"성산일출봉\"}}]}]}"));
        courses.store.add(course(arrayCourse, me, "배열 placeId",
                "{\"days\":[{\"day\":1,\"stops\":[{\"place\":{\"placeId\":[\"ChIJ_GOOGLE_ID\"],"
                        + "\"placeName\":\"성산일출봉\"}}]}]}"));

        for (UUID courseId : List.of(objectCourse, arrayCourse)) {
            List<Itinerary.Stop> stops =
                    service().getCourse(me, courseId).course().itinerary().days().get(0).stops();

            assertThat(stops).hasSize(1);
            assertThat(stops.get(0).place().placeId()).isNull();
            assertThat(stops.get(0).place().placeName()).isEqualTo("성산일출봉");
        }
    }

    /**
     * 명세 개정 전 저장된 코스는 {@code transportToNext}가 문자열이다. 객체로 읽으려다 예외가 나면
     * 그 코스의 상세 조회 전체가 500이 되므로, 이동 수단만 담은 객체로 승격해 읽어야 한다.
     */
    @Test
    void 옛_형식의_문자열_transportToNext도_읽을_수_있다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "예전 코스",
                "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,\"placeName\":\"성산일출봉\","
                        + "\"transportToNext\":\"walking\"}]}]}"));

        Itinerary.Stop stop = service().getCourse(me, courseId)
                .course().itinerary().days().get(0).stops().get(0);

        assertThat(stop.transportToNext().type()).isEqualTo("walking");
        assertThat(stop.transportToNext().minutes()).isNull();
        assertThat(stop.place()).isNull();
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

    @Test
    void 소유자면_코스를_삭제하고_목록에서_제외된다() {
        UUID me = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, me, "내 코스", "{\"days\":[]}"));

        service().deleteCourse(me, courseId);

        assertThat(courses.findById(courseId)).isEmpty();
        assertThat(service().getMyCourses(me).courses()).isEmpty();
    }

    @Test
    void 삭제할_코스가_없으면_404() {
        assertThatThrownBy(() -> service().deleteCourse(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 타인_코스를_삭제하면_403이고_삭제되지_않는다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, owner, "남의 코스", "{\"days\":[]}"));

        assertThatThrownBy(() -> service().deleteCourse(UUID.randomUUID(), courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_DELETE_ACCESS_DENIED);
        assertThat(courses.findById(courseId)).isPresent();
    }

    // ===== 친구 초대로 공유받은 코스 (DOM-6) =====

    /** DOM-6 §코스 목록에서의 처리 — "공유받은 코스를 수락하면 해당 코스는 여행 코스 목록에 표시된다." */
    @Test
    void 공유받은_코스도_내_목록에_함께_보인다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        courses.store.add(course(UUID.randomUUID(), me, "내 코스", "{\"days\":[]}"));
        courses.store.add(course(sharedId, friend, "친구가 공유한 코스", "{\"days\":[]}"));
        sharing.grant(sharedId, me);

        CourseListResponse response = service().getMyCourses(me);

        assertThat(response.courses()).extracting(CourseListResponse.CourseSummary::title)
                .containsExactlyInAnyOrder("내 코스", "친구가 공유한 코스");
    }

    @Test
    void 수락하지_않은_친구_코스는_목록에_보이지_않는다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        courses.store.add(course(UUID.randomUUID(), friend, "수락 안 한 코스", "{\"days\":[]}"));

        assertThat(service().getMyCourses(me).courses()).isEmpty();
    }

    /** 소유 코스와 공유 코스를 합쳐도 응답의 createdAt 과 같은 기준(최신순)으로 정렬돼야 한다. */
    @Test
    void 소유_코스와_공유_코스를_생성_시각_최신순으로_섞어_정렬한다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        Instant base = Instant.parse("2026-08-01T00:00:00Z");
        courses.store.add(course(UUID.randomUUID(), me, "내 옛 코스", "{\"days\":[]}", base));
        courses.store.add(course(sharedId, friend, "공유 코스", "{\"days\":[]}", base.plusSeconds(60)));
        courses.store.add(course(UUID.randomUUID(), me, "내 새 코스", "{\"days\":[]}", base.plusSeconds(120)));
        sharing.grant(sharedId, me);

        CourseListResponse response = service().getMyCourses(me);

        assertThat(response.courses()).extracting(CourseListResponse.CourseSummary::title)
                .containsExactly("내 새 코스", "공유 코스", "내 옛 코스");
    }

    /** DOM-6 §권한 정책 — 공유받은 사용자는 "공유받은 코스 상세 조회"를 할 수 있다. */
    @Test
    void 공유받은_사용자는_코스_상세를_조회할_수_있다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, friend, "공유 코스", "{\"days\":[{\"day\":1,\"stops\":[]}]}"));
        sharing.grant(courseId, me);

        CourseDetailResponse response = service().getCourse(me, courseId);

        assertThat(response.course().courseId()).isEqualTo(courseId.toString());
        assertThat(response.course().userId()).isEqualTo(friend.toString());
    }

    /**
     * DOM-6 §보안 및 운영 정책 — "공유받은 사용자의 삭제 동작은 원본 삭제가 아니라 접근 권한
     * 제거로 처리한다."
     */
    @Test
    void 공유받은_사용자의_삭제는_원본을_지우지_않고_내_목록에서만_제거한다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, friend, "공유 코스", "{\"days\":[]}"));
        sharing.grant(courseId, me);

        service().deleteCourse(me, courseId);

        assertThat(courses.findById(courseId)).isPresent();
        assertThat(service().getMyCourses(me).courses()).isEmpty();
        assertThat(service().getMyCourses(friend).courses()).hasSize(1);
    }

    /** 목록에서 제거한 뒤에는 다시 남의 코스일 뿐이므로 상세도 403이어야 한다. */
    @Test
    void 목록에서_제거한_공유_코스는_상세도_403이_된다() {
        UUID me = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, friend, "공유 코스", "{\"days\":[]}"));
        sharing.grant(courseId, me);
        service().deleteCourse(me, courseId);

        assertThatThrownBy(() -> service().getCourse(me, courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_ACCESS_DENIED);
    }

    /** 소유자가 원본을 지우면 공유받은 사용자 목록에도 열 수 없는 코스가 남지 않아야 한다. */
    @Test
    void 소유자가_코스를_삭제하면_공유도_함께_정리된다() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        courses.store.add(course(courseId, owner, "내 코스", "{\"days\":[]}"));
        sharing.grant(courseId, guest);

        service().deleteCourse(owner, courseId);

        assertThat(sharing.revokedCourses).containsExactly(courseId);
        assertThat(sharing.isSharedWith(courseId, guest)).isFalse();
        assertThat(service().getMyCourses(guest).courses()).isEmpty();
    }
}
