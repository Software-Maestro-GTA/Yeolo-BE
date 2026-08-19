package com.soma.yeolo.user.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 정보 (DOM-3). Google OAuth 기반 사용자 계정.
 * (provider, provider_user_id) 조합으로 유일 식별한다.
 */
@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_provider_provider_user_id",
                columnNames = {"provider", "provider_user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false)
    private Provider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /*
     * 사용자가 직접 고친 항목 표시 (API-USER-1). true인 항목은 재로그인 시 제공자 값으로 덮지 않는다
     * — 근거는 아래 updateOnLogin 문서 참고. 항목별로 나눈 것은 프로필 수정이 부분 수정이기 때문이다.
     * 이름만 고친 사용자의 이메일까지 제공자 추종을 끊으면, 제공자가 이메일을 바꿔도 옛 주소가 남는다.
     *
     * columnDefinition으로 DB 기본값을 주는 이유: dev는 ddl-auto=update라 이미 행이 있는 users에
     * 컬럼을 덧붙이는데, 기본값 없는 NOT NULL 추가는 PostgreSQL에서 실패한다(기존 행이 NULL).
     * nullable 속성을 쓰지 않는 것은 Hibernate가 여기에 not null을 한 번 더 붙이지 않게 하려는 것이다.
     */
    @Column(name = "email_customized", columnDefinition = "boolean not null default false")
    private boolean emailCustomized;

    @Column(name = "display_name_customized", columnDefinition = "boolean not null default false")
    private boolean displayNameCustomized;

    @Column(name = "profile_image_customized", columnDefinition = "boolean not null default false")
    private boolean profileImageCustomized;

    @Builder
    private User(Provider provider, String providerUserId, String email,
                 String displayName, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.status = UserStatus.ACTIVE;
        this.lastLoginAt = Instant.now();
    }

    /** 신규 OAuth 사용자 생성. status=active, 최초 로그인 시각 기록. */
    public static User createOAuthUser(Provider provider, String providerUserId, String email,
                                       String displayName, String profileImageUrl) {
        return User.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .email(email)
                .displayName(displayName)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    /**
     * 기존 사용자 재로그인 시 프로필을 제공자 값과 맞추고 마지막 로그인 시각을 갱신한다.
     *
     * <p>덮어쓰는 항목은 <b>사용자가 직접 고치지 않았고</b>(=이 클래스의 {@code *Customized} 표시가
     * 꺼져 있고) <b>제공자가 값을 실제로 준</b> 항목뿐이다. 두 조건은 각각 다음을 막는다.
     *
     * <ul>
     *   <li>고친 항목까지 덮으면 {@link #updateProfile}로 수정한 프로필이 다음 로그인에 되돌아간다
     *       — 사용자에게는 "수정이 저장되지 않는" 것으로 보인다.</li>
     *   <li>제공자가 준 {@code null}까지 반영하면 값이 지워진다. Apple은 최초 동의 이후 이름·사진을
     *       주지 않고 이메일도 생략될 수 있어(DOM-1 §프로필 정보 처리 기준), 로그인 한 번에
     *       가입 때 받은 정보가 사라진다. 미제공은 "지워 달라"가 아니라 "모른다"이다.</li>
     * </ul>
     *
     * <p>즉 손대지 않은 항목은 제공자를 계속 따라가고, 손댄 항목만 사용자 값으로 굳는다.
     */
    public void updateOnLogin(String email, String displayName, String profileImageUrl) {
        if (!this.emailCustomized && email != null) {
            this.email = email;
        }
        if (!this.displayNameCustomized && displayName != null) {
            this.displayName = displayName;
        }
        if (!this.profileImageCustomized && profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
        this.lastLoginAt = Instant.now();
    }

    /**
     * 사용자가 직접 수정한 프로필을 반영한다 (API-USER-1).
     *
     * <p>{@code null}인 항목은 <b>변경하지 않는다</b>. PATCH이고 DOM-1상 두 항목 모두 nullable이라
     * "안 보냄"과 "null로 지움"을 요청 본문만으로 구분할 수 없는데, 안 보낸 항목을 null로 덮으면
     * 이름만 고쳐도 프로필 이미지가 지워진다. 지우는 쪽이 아니라 유지하는 쪽을 기본값으로 둔다.
     *
     * <p>반영한 항목은 "사용자가 고쳤다"로 표시해, 이후 로그인이 제공자 값으로 되돌리지 않게 한다
     * ({@link #updateOnLogin}). 표시는 되돌리지 않는다 — 한 번 직접 정한 항목의 주인은 사용자다.
     *
     * <p><b>이메일은 더 이상 여기서 바뀌지 않는다</b> — 명세 개정으로 API-USER-1 요청에서 빠졌다.
     * {@code emailCustomized}는 그래서 새로 켜지지 않지만, 이미 켜진 사용자(개정 전 이메일을 직접
     * 고친 계정)의 값을 재로그인이 덮지 않도록 판정 자체는 남겨 둔다.
     */
    public void updateProfile(String displayName, String profileImageUrl) {
        if (displayName != null) {
            this.displayName = displayName;
            this.displayNameCustomized = true;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
            this.profileImageCustomized = true;
        }
    }

    /**
     * 회원탈퇴 (API-USER-2). 계정을 소프트 삭제(status=deleted)하면서 개인정보를 파기한다.
     *
     * <p>이미 탈퇴한 계정이면 아무것도 하지 않는다(멱등) — 두 번째 호출이 탈퇴 시각을 덮어써
     * 파기 시점 기록이 흐트러지지 않게 한다.
     */
    public void withdraw() {
        if (isWithdrawn()) {
            return;
        }
        this.status = UserStatus.DELETED;
        this.deletedAt = Instant.now();
        releaseOAuthIdentity();
    }

    /**
     * 탈퇴 처리된 계정인지. {@code deletedAt}은 탈퇴 시에만 채워지므로 이 값 하나로 판정한다
     * — 인증 필터의 탈퇴자 차단({@code UserRepository.existsByIdAndDeletedAtIsNotNull})과 같은 기준이다.
     */
    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }

    /**
     * OAuth 식별자를 회수하고 계정의 식별정보를 파기한다.
     *
     * <p>식별자는 지우지 않고 {@code deleted:<id>}로 치환한다. 원본 sub를 남기지 않으면서도
     * {@code (provider, provider_user_id)} 유니크 제약을 비워 줘, 같은 계정으로 재가입하면
     * 탈퇴한 옛 레코드가 아니라 새 사용자로 인식된다.
     *
     * <p>탈퇴({@link #withdraw})가 부르는 것이 정상 경로지만, <b>치환 없이 탈퇴 표시만 남은 옛 행</b>을
     * 로그인 시점에 뒤늦게 정리하는 데도 쓴다({@code UserService.upsertOnOAuthLogin}). 그런 행이
     * 실재한다 — 탈퇴 기능 최초 구현(#42)은 {@code status}·{@code deletedAt}만 설정했고, 식별자 치환은
     * 그 다음 개정(#44)에서 추가됐다. 그 사이에 탈퇴한 계정은 원본 sub를 그대로 들고 있어, 재로그인이
     * 새 사용자가 아니라 <b>탈퇴 계정 자체를 되살린다</b>.
     */
    public void releaseOAuthIdentity() {
        this.providerUserId = "deleted:" + this.id;
        this.email = null;
        this.displayName = null;
        this.profileImageUrl = null;
    }
}
