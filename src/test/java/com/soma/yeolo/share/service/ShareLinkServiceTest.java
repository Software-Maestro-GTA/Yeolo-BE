package com.soma.yeolo.share.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.share.domain.CourseAccess;
import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.share.domain.ShareLink;
import com.soma.yeolo.share.domain.ShareToken;
import com.soma.yeolo.share.dto.ShareAcceptResponse;
import com.soma.yeolo.share.dto.ShareLinkCreateResponse;
import com.soma.yeolo.share.dto.SharePreviewResponse;
import com.soma.yeolo.share.service.port.CourseAccessRepository;
import com.soma.yeolo.share.service.port.ShareLinkRepository;
import com.soma.yeolo.user.domain.UserDisplayProfile;
import com.soma.yeolo.user.service.UserDisplayProfileReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 친구 초대 공유 링크 서비스 (API-SHARE-1 생성 / API-SHARE-2 조회 / API-SHARE-3 수락, DOM-6).
 * 영속 포트는 손수 짠 fake로 대체해 DB 없이 검증한다 (docs/architecture.md §8).
 */
class ShareLinkServiceTest {

    /** 공유 링크 영속 포트 fake: 토큰 해시로 조회하고, 저장 시 식별자를 부여한다. */
    private static final class FakeShareLinkRepository implements ShareLinkRepository {
        private final Map<String, SavedShareLink> byTokenHash = new LinkedHashMap<>();
        private final List<ShareLink> saved = new ArrayList<>();

        @Override
        public UUID save(ShareLink link) {
            UUID id = UUID.randomUUID();
            saved.add(link);
            byTokenHash.put(link.tokenHash(),
                    new SavedShareLink(id, link.courseId(), link.inviterId(), link.expiresAt(), null));
            return id;
        }

        @Override
        public Optional<SavedShareLink> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byTokenHash.get(tokenHash));
        }

        @Override
        public void revokeAllByCourseId(UUID courseId, Instant revokedAt) {
            byTokenHash.replaceAll((hash, link) -> link.courseId().equals(courseId) && !link.isRevoked()
                    ? new SavedShareLink(link.id(), link.courseId(), link.inviterId(),
                            link.expiresAt(), revokedAt)
                    : link);
        }

        /** 테스트에서 만료·회수 상태를 직접 만들기 위한 보조 메서드. */
        void put(String tokenHash, SavedShareLink link) {
            byTokenHash.put(tokenHash, link);
        }
    }

    /** 코스 접근 권한 포트 fake. 유니크 제약(코스, 사용자)까지 실제 어댑터와 같게 흉내낸다. */
    private static final class FakeCourseAccessRepository implements CourseAccessRepository {
        private final List<CourseAccess> store = new ArrayList<>();

        /** 사전 검사를 건너뛴 것처럼 보이게 해 저장 시점 중복 판정만 남기는 스위치(경합 재현용). */
        private boolean pretendAbsent;

        @Override
        public boolean saveIfAbsent(CourseAccess access) {
            // 유니크 제약은 pretendAbsent 와 무관하게 언제나 걸린다 — DB가 최종 방어선이라는 뜻이다.
            if (contains(access.courseId(), access.userId())) {
                return false;
            }
            store.add(access);
            return true;
        }

        private boolean contains(UUID courseId, UUID userId) {
            return store.stream()
                    .anyMatch(a -> a.courseId().equals(courseId) && a.userId().equals(userId));
        }

        @Override
        public boolean exists(UUID courseId, UUID userId) {
            if (pretendAbsent) {
                return false;
            }
            return store.stream()
                    .anyMatch(a -> a.courseId().equals(courseId) && a.userId().equals(userId));
        }

        @Override
        public List<UUID> findCourseIdsByUserId(UUID userId) {
            return store.stream().filter(a -> a.userId().equals(userId))
                    .map(CourseAccess::courseId).toList();
        }

        @Override
        public boolean delete(UUID courseId, UUID userId) {
            return store.removeIf(a -> a.courseId().equals(courseId) && a.userId().equals(userId));
        }

        @Override
        public void deleteAllByCourseId(UUID courseId) {
            store.removeIf(a -> a.courseId().equals(courseId));
        }
    }

    /** 코스 영속 포트 fake: 공유가 읽는 것은 코스 조회뿐이다. */
    private static final class FakeCourseRepository implements CourseRepository {
        private final List<SavedCourse> store = new ArrayList<>();

        @Override
        public UUID save(Course course) {
            throw new UnsupportedOperationException("공유 테스트에서는 코스 저장을 사용하지 않는다.");
        }

        @Override
        public List<SavedCourse> findByUserIdLatestFirst(UUID userId) {
            throw new UnsupportedOperationException("공유 테스트에서는 목록 조회를 사용하지 않는다.");
        }

        @Override
        public List<SavedCourse> findAllByIdsLatestFirst(Collection<UUID> courseIds) {
            return store.stream().filter(c -> courseIds.contains(c.courseId())).toList();
        }

        @Override
        public Optional<UUID> findRecentCourseId(UUID userId, Collection<UUID> sharedCourseIds) {
            throw new UnsupportedOperationException("공유 테스트에서는 최근 코스 조회를 사용하지 않는다.");
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
            throw new UnsupportedOperationException("공유 테스트에서는 존재 조회를 사용하지 않는다.");
        }
    }

    private final FakeShareLinkRepository links = new FakeShareLinkRepository();
    private final FakeCourseAccessRepository accesses = new FakeCourseAccessRepository();
    private final FakeCourseRepository courses = new FakeCourseRepository();
    private final Map<UUID, UserDisplayProfile> profiles = new LinkedHashMap<>();
    private final UserDisplayProfileReader profileReader =
            userId -> Optional.ofNullable(profiles.get(userId));

    private ShareLinkService service() {
        return service(Duration.ofDays(7));
    }

    private ShareLinkService service(Duration ttl) {
        return new ShareLinkService(links, accesses, courses, profileReader,
                new ShareLinkProperties("https://yeolo.vercel.app/invite", ttl));
    }

    private UUID givenCourse(UUID ownerId, String title) {
        UUID courseId = UUID.randomUUID();
        courses.store.add(new SavedCourse(courseId, ownerId, title, "일본", "오사카", null,
                LocalDate.of(2026, 9, 1), 4, List.of("맛집"), "이유", "{\"days\":[]}", Instant.now()));
        return courseId;
    }

    // ===== API-SHARE-1 공유 링크 생성 =====

    @Test
    void 소유자는_공유_링크를_발급받는다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");

        ShareLinkCreateResponse response = service().createShareLink(owner, courseId);

        assertThat(response.shareToken()).isNotBlank();
        assertThat(response.shareUrl()).isEqualTo("https://yeolo.vercel.app/invite/" + response.shareToken());
        assertThat(response.expiresAt()).isNotNull();
    }

    /** DOM-6 §보안 및 운영 정책 — 링크는 서버가 관리하는 임시 식별자이며 courseId를 노출하지 않는다. */
    @Test
    void 공유_URL에_courseId가_들어가지_않는다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");

        ShareLinkCreateResponse response = service().createShareLink(owner, courseId);

        assertThat(response.shareUrl()).doesNotContain(courseId.toString());
        assertThat(response.shareToken()).doesNotContain(courseId.toString());
    }

    /** Refresh Token과 같은 정책 — 초대 식별자의 평문은 저장하지 않는다 (docs/architecture.md §3). */
    @Test
    void 토큰은_평문이_아니라_해시로_저장된다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");

        ShareLinkCreateResponse response = service().createShareLink(owner, courseId);

        assertThat(links.saved).hasSize(1);
        assertThat(links.saved.get(0).tokenHash())
                .isEqualTo(ShareToken.of(response.shareToken()).hash())
                .isNotEqualTo(response.shareToken());
    }

    @Test
    void ttl이_무기한이면_expiresAt이_null이다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");

        assertThat(service(Duration.ZERO).createShareLink(owner, courseId).expiresAt()).isNull();
    }

    @Test
    void 코스가_없으면_링크_발급은_404() {
        assertThatThrownBy(() -> service().createShareLink(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 타인_코스의_링크를_발급하면_403() {
        UUID courseId = givenCourse(UUID.randomUUID(), "남의 코스");

        assertThatThrownBy(() -> service().createShareLink(UUID.randomUUID(), courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_SHARE_ACCESS_DENIED);
    }

    /** 해시만 저장하므로 기존 토큰을 되돌려줄 수 없다 — 호출마다 새 링크를 발급하고 둘 다 유효하다. */
    @Test
    void 다시_요청하면_새_토큰이_발급되고_이전_링크도_살아있다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");

        String first = service().createShareLink(owner, courseId).shareToken();
        String second = service().createShareLink(owner, courseId).shareToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(service().getPreview(first).course().title()).isEqualTo("오사카 4일");
        assertThat(service().getPreview(second).course().title()).isEqualTo("오사카 4일");
    }

    // ===== API-SHARE-2 공유 링크 조회(미리보기) =====

    @Test
    void 미리보기는_코스_요약과_초대자를_반환한다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        profiles.put(owner, new UserDisplayProfile("승우", "https://cdn/profile.png"));
        String token = service().createShareLink(owner, courseId).shareToken();

        SharePreviewResponse preview = service().getPreview(token);

        assertThat(preview.course().title()).isEqualTo("오사카 4일");
        assertThat(preview.course().destinationCountry()).isEqualTo("일본");
        assertThat(preview.course().destinationCity()).isEqualTo("오사카");
        assertThat(preview.course().startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(preview.course().totalDays()).isEqualTo(4);
        assertThat(preview.inviter().displayName()).isEqualTo("승우");
        assertThat(preview.inviter().profileImageUrl()).isEqualTo("https://cdn/profile.png");
        assertThat(preview.expiresAt()).isNotNull();
    }

    /** 비로그인 사용자도 받는 응답이므로 코스 식별자·일정이 새어 나가면 안 된다 (DOM-6). */
    @Test
    void 미리보기_응답에는_courseId가_없다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();

        assertThat(service().getPreview(token).toString()).doesNotContain(courseId.toString());
    }

    @Test
    void 초대자를_찾지_못하면_이름과_이미지는_null이다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();

        SharePreviewResponse preview = service().getPreview(token);

        assertThat(preview.inviter().displayName()).isNull();
        assertThat(preview.inviter().profileImageUrl()).isNull();
    }

    @Test
    void 없는_토큰을_조회하면_404() {
        assertThatThrownBy(() -> service().getPreview("존재하지-않는-토큰"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SHARE_LINK);
    }

    /**
     * DOM-6 §예외 상황 — "공유된 코스가 삭제된 경우"는 "유효하지 않은 링크"와 구분해 안내한다.
     * 코스를 지우면 링크도 회수되므로, 회수(410)보다 코스 존재(404)를 먼저 봐야 이 구분이 살아 있다.
     */
    @Test
    void 원본_코스가_삭제되면_회수된_링크여도_코스_없음_404다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();

        links.revokeAllByCourseId(courseId, Instant.now());
        courses.deleteById(courseId);

        assertThatThrownBy(() -> service().getPreview(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    void 만료된_링크를_조회하면_410() {
        String token = givenLinkWith(Instant.now().minusSeconds(60), null);

        assertThatThrownBy(() -> service().getPreview(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_GONE);
    }

    @Test
    void 회수된_링크를_조회하면_410() {
        String token = givenLinkWith(Instant.now().plusSeconds(3600), Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> service().getPreview(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_GONE);
    }

    // ===== API-SHARE-3 공유 링크 수락 =====

    @Test
    void 수락하면_코스_식별자를_돌려주고_접근_권한이_부여된다() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();

        ShareAcceptResponse response = service().accept(guest, token);

        assertThat(response.courseId()).isEqualTo(courseId.toString());
        assertThat(accesses.findCourseIdsByUserId(guest)).containsExactly(courseId);
    }

    /** DOM-6 §예외 상황 — 자기 자신의 코스는 수락 대상이 아니다. 명세(API-SHARE-3)의 400을 따른다. */
    @Test
    void 자기_코스를_수락하면_400이고_권한이_생기지_않는다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();

        assertThatThrownBy(() -> service().accept(owner, token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);
        assertThat(accesses.findCourseIdsByUserId(owner)).isEmpty();
    }

    /** DOM-6 §보안 및 운영 정책 — "같은 공유 링크를 여러 번 수락해도 중복으로 코스가 추가되지 않는다." */
    @Test
    void 이미_수락한_링크를_다시_수락하면_400이고_중복_추가되지_않는다() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();
        service().accept(guest, token);

        assertThatThrownBy(() -> service().accept(guest, token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);
        assertThat(accesses.findCourseIdsByUserId(guest)).containsExactly(courseId);
    }

    /**
     * 수락 버튼 연타로 두 요청이 동시에 "아직 수락 안 함"을 본 상황. 사전 검사를 통과해도 저장에서
     * 걸러져 명세의 400이 나와야 한다 — 유니크 제약 위반이 그대로 새면 500 + ERROR 로그가 된다.
     */
    @Test
    void 사전_검사를_통과한_경합_수락도_500이_아니라_400이다() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();
        service().accept(guest, token);
        accesses.pretendAbsent = true;

        assertThatThrownBy(() -> service().accept(guest, token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);

        accesses.pretendAbsent = false;
        assertThat(accesses.findCourseIdsByUserId(guest)).containsExactly(courseId);
    }

    /** 같은 코스의 새 링크로 다시 수락해도 중복 추가는 막아야 한다 — 판정 기준은 링크가 아니라 코스다. */
    @Test
    void 같은_코스의_다른_링크로_다시_수락해도_400이다() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        service().accept(guest, service().createShareLink(owner, courseId).shareToken());
        String another = service().createShareLink(owner, courseId).shareToken();

        assertThatThrownBy(() -> service().accept(guest, another))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_NOT_ACCEPTABLE);
        assertThat(accesses.findCourseIdsByUserId(guest)).containsExactly(courseId);
    }

    @Test
    void 없는_토큰을_수락하면_404() {
        assertThatThrownBy(() -> service().accept(UUID.randomUUID(), "존재하지-않는-토큰"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SHARE_LINK);
    }

    @Test
    void 만료된_링크를_수락하면_410이고_권한이_생기지_않는다() {
        UUID guest = UUID.randomUUID();
        String token = givenLinkWith(Instant.now().minusSeconds(60), null);

        assertThatThrownBy(() -> service().accept(guest, token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHARE_LINK_GONE);
        assertThat(accesses.findCourseIdsByUserId(guest)).isEmpty();
    }

    @Test
    void 원본_코스가_삭제된_링크를_수락하면_404다() {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        String token = service().createShareLink(owner, courseId).shareToken();
        courses.deleteById(courseId);

        assertThatThrownBy(() -> service().accept(UUID.randomUUID(), token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COURSE_NOT_FOUND);
    }

    /** 만료·회수 상태의 링크를 직접 심는다 — 발급 API로는 만들 수 없는 상태이기 때문이다. */
    private String givenLinkWith(Instant expiresAt, Instant revokedAt) {
        UUID owner = UUID.randomUUID();
        UUID courseId = givenCourse(owner, "오사카 4일");
        ShareToken token = ShareToken.issue();
        links.put(token.hash(),
                new SavedShareLink(UUID.randomUUID(), courseId, owner, expiresAt, revokedAt));
        return token.value();
    }
}
