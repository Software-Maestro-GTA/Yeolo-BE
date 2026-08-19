package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.course.entity.CourseEntity;
import com.soma.yeolo.course.repository.CourseJpaRepository;
import com.soma.yeolo.global.config.JpaAuditingConfig;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.entity.TasteProfileEntity;
import com.soma.yeolo.tasteprofile.repository.TasteProfileJpaRepository;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * 탈퇴 후 재가입이 <b>새 사용자</b>가 되는지 DB에 붙여 확인한다.
 *
 * <p>서비스 단위 테스트(목 리포지토리)로는 증명되지 않는 것이 두 가지라 여기서 검증한다 —
 * {@code (provider, provider_user_id)} 유니크 제약이 실제로 통과하는지(=식별자 회수와 신규 INSERT의
 * 순서), 그리고 옛 행의 정리가 영속화되는지다.
 *
 * <p>재현하는 데이터는 <b>탈퇴 표시만 남고 식별자는 원본 sub 그대로인 행</b>이다. 탈퇴 최초
 * 구현(#42)이 {@code status}·{@code deletedAt}만 설정했고 식별자 치환·개인정보 파기는 다음
 * 개정(#44)에서 추가돼, 그 사이에 탈퇴한 계정이 이 모양으로 남아 있다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)  // 슬라이스 테스트는 @Configuration을 안 집어 온다 — created_at 감사값 필요
// Boot가 갈아끼우는 테스트용 임베디드 H2에서는 Hibernate가 만든 enum 체크 제약(provider/status)이
// "The database has been closed"로 깨져 users insert가 전부 실패한다. 테스트 설정의 H2
// (DB_CLOSE_DELAY=-1)를 그대로 쓰면 정상 동작하므로 교체를 끈다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserReSignUpPersistenceTest {

    private static final String SUB = "google-sub-legacy";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseJpaRepository courseJpaRepository;

    @Autowired
    private TasteProfileJpaRepository tasteProfileJpaRepository;

    @Autowired
    private EntityManager em;

    /** 슬라이스 테스트는 {@code @Service}를 안 집어 오므로 포트 구현을 리포지토리에 직접 위임한다. */
    private UserService userService() {
        return new UserService(userRepository, tasteProfileJpaRepository::deleteByUserId);
    }

    /** #42 시절 탈퇴 데이터 재현: 상태·탈퇴 시각만 남기고 식별자·식별정보는 건드리지 않는다. */
    private UUID legacyWithdrawnUser() {
        User user = userRepository.save(
                User.createOAuthUser(Provider.GOOGLE, SUB, "old@gmail.com", "옛이름", "http://old"));
        UUID userId = user.getId();
        em.flush();
        em.createNativeQuery(
                        "update users set status = 'deleted', deleted_at = current_timestamp where id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        em.clear();
        return userId;
    }

    private UUID saveCourse(UUID ownerId) {
        CourseEntity course = courseJpaRepository.save(CourseEntity.builder()
                .userId(ownerId)
                .title("옛 코스")
                .destinationCountry("대한민국")
                .destinationCity("서울")
                .startDate(LocalDate.of(2026, 8, 1))
                .totalDays(1)
                .itinerary("{}")
                .build());
        em.flush();
        em.clear();
        return course.getId();
    }

    @Test
    void 식별자가_남은_탈퇴_행이_있어도_재가입은_새_행으로_저장된다() {
        UUID withdrawnId = legacyWithdrawnUser();

        User created = userService().upsertOnOAuthLogin(
                new OAuthUserInfo(Provider.GOOGLE, SUB, "new@gmail.com", "재가입유저", "http://new"));
        em.flush();
        em.clear();

        // 새 사용자다 — 탈퇴 계정을 되살리지 않았다.
        assertThat(created.getId()).isNotEqualTo(withdrawnId);
        User reloadedNew = userRepository.findById(created.getId()).orElseThrow();
        assertThat(reloadedNew.getProviderUserId()).isEqualTo(SUB);
        assertThat(reloadedNew.getDeletedAt()).isNull();
        assertThat(reloadedNew.getEmail()).isEqualTo("new@gmail.com");
    }

    @Test
    void 재가입_시_옛_행의_식별자_회수와_개인정보_파기가_DB에_반영된다() {
        UUID withdrawnId = legacyWithdrawnUser();

        userService().upsertOnOAuthLogin(
                new OAuthUserInfo(Provider.GOOGLE, SUB, "new@gmail.com", "재가입유저", "http://new"));
        em.flush();
        em.clear();

        User reloadedOld = userRepository.findById(withdrawnId).orElseThrow();
        assertThat(reloadedOld.getProviderUserId()).isEqualTo("deleted:" + withdrawnId);
        assertThat(reloadedOld.getEmail()).isNull();
        assertThat(reloadedOld.getDisplayName()).isNull();
        assertThat(reloadedOld.getProfileImageUrl()).isNull();
        assertThat(reloadedOld.getDeletedAt()).isNotNull();  // 탈퇴 기록 자체는 남는다
    }

    /**
     * 신고된 증상 그대로의 검증 — 재가입 계정에는 옛 계정의 코스가 딸려오지 않는다. 되살리던 시절엔
     * 로그인 응답의 {@code recentCourseId}가 옛 코스를 가리켰다(같은 질의를 쓴다).
     */
    @Test
    void 재가입_사용자에게는_옛_계정의_코스가_보이지_않는다() {
        UUID withdrawnId = legacyWithdrawnUser();
        UUID oldCourseId = saveCourse(withdrawnId);

        User created = userService().upsertOnOAuthLogin(
                new OAuthUserInfo(Provider.GOOGLE, SUB, "new@gmail.com", "재가입유저", "http://new"));
        em.flush();
        em.clear();

        assertThat(courseJpaRepository.findIdsByUserIdLatestFirst(created.getId(), PageRequest.of(0, 1)))
                .isEmpty();
        // 코스 자체는 파기하지 않고 삭제된 소유자를 참조한 채 남는다 (API-USER-2)
        assertThat(courseJpaRepository.findIdsByUserIdLatestFirst(withdrawnId, PageRequest.of(0, 1)))
                .containsExactly(oldCourseId);
    }

    /**
     * 그 시절 탈퇴(#42)는 Refresh Token만 지웠으므로 옛 행에는 취향 프로필이 남아 있다. 사진 EXIF에서
     * 파생된 이동 이력이라 계정 행만 비우면 파기가 반쪽이 되고, 이미 탈퇴한 계정은 탈퇴를 다시 태워도
     * {@code withdraw()}가 멱등하게 아무것도 하지 않아 지울 방법이 없다.
     */
    @Test
    void 재가입_시_옛_계정의_취향_프로필도_DB에서_파기된다() {
        UUID withdrawnId = legacyWithdrawnUser();
        tasteProfileJpaRepository.save(TasteProfileEntity.builder()
                .userId(withdrawnId)
                .sourceType(SourceType.BEHAVIOR)
                .profile("{}")
                .build());
        em.flush();
        em.clear();

        userService().upsertOnOAuthLogin(
                new OAuthUserInfo(Provider.GOOGLE, SUB, "new@gmail.com", "재가입유저", "http://new"));
        em.flush();
        em.clear();

        assertThat(tasteProfileJpaRepository.findAll()).isEmpty();
    }
}
