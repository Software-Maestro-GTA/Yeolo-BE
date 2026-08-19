package com.soma.yeolo.user.service;

import com.soma.yeolo.user.domain.UserDisplayProfile;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import com.soma.yeolo.user.service.port.TasteProfileEraser;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDisplayProfileReader {

    private final UserRepository userRepository;
    private final TasteProfileEraser tasteProfileEraser;

    /**
     * OAuth 로그인 시 사용자 upsert.
     * (provider, providerUserId) 기준으로 <b>탈퇴하지 않은</b> 기존 사용자면 프로필/로그인 시각을
     * 갱신하고, 없으면 신규 생성한다.
     *
     * <p>프로필 갱신은 사용자가 API-USER-1로 직접 고치지 않은 항목에만 적용된다 — 판단은
     * 엔티티가 한다({@link User#updateOnLogin}).
     *
     * <p><b>탈퇴 계정은 "없는 사용자"로 취급한다.</b> 조회에 걸린 행이 탈퇴 상태면 되살리지 않고,
     * 식별자를 회수한 뒤 새 사용자를 만든다. (재가입 처리는 API-USER-2에 규정이 없는 BE 결정이다
     * — 명세에 반영할 대상.) 탈퇴가 식별자를
     * {@code deleted:<id>}로 치환하므로 보통은 조회 자체가 빗나가지만, <b>치환 없이 탈퇴 표시만
     * 남은 옛 행</b>(#42~#44 사이 탈퇴)이 실재한다. 그 행이 걸리면 재가입한 사용자가 탈퇴 계정을
     * 그대로 되살려, 옛 코스를 가리키는 {@code recentCourseId}를 받고도 이후 모든 API는 인증
     * 필터의 탈퇴자 차단에 걸려 401이 된다 — 로그인은 되는데 아무것도 못 하는 상태다. 파기했던
     * 이메일·이름·프로필 이미지가 {@link User#updateOnLogin}으로 복원되는 문제도 함께 따라온다.
     *
     * <p>정리는 계정 행에서 끝내지 않고 <b>취향 프로필까지 파기</b>한다 — 그 시절 탈퇴(#42)는
     * Refresh Token만 지웠고 취향 프로필 파기는 #44에서 추가됐다. 취향 프로필은 사진 EXIF에서 파생된
     * 개인의 이동 이력이라({@link TasteProfileEraser}) 여기서 지우지 않으면 영구히 남는다 —
     * {@code withdraw()}는 이미 탈퇴한 계정에 멱등하게 아무것도 하지 않으므로 탈퇴를 다시 태울 수도 없다.
     * 저장소의 프로필 이미지는 대상이 아니다. 이미지 업로드·저장소 파기는 #78(2026-08)에 들어와
     * 영향 구간(#42~#44, 2026-07-29~30)보다 늦다 — 그때의 {@code profile_image_url}은 제공자 URL이라
     * 우리 저장소에 파일이 없다. 로그인 경로에 S3 호출을 끼워 넣지 않는 이득도 있다.
     *
     * <p>식별자 회수를 <b>먼저 flush</b>하는 것이 중요하다. JPA는 쓰기를 트랜잭션 끝까지 미루므로
     * 순서를 강제하지 않으면 새 행 INSERT가 옛 행 UPDATE보다 앞서 나가
     * {@code (provider, provider_user_id)} 유니크 제약을 위반한다. 같은 트랜잭션이라 중간에 실패하면
     * 둘 다 롤백된다.
     */
    @Transactional
    public User upsertOnOAuthLogin(OAuthUserInfo info) {
        Optional<User> found =
                userRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId());

        if (found.isPresent()) {
            User user = found.get();
            if (!user.isWithdrawn()) {
                user.updateOnLogin(info.email(), info.displayName(), info.profileImageUrl());
                return user;
            }
            user.releaseOAuthIdentity();
            userRepository.saveAndFlush(user);
            tasteProfileEraser.eraseAll(user.getId());
        }

        return userRepository.save(User.createOAuthUser(
                info.provider(), info.providerUserId(), info.email(),
                info.displayName(), info.profileImageUrl()));
    }

    /**
     * 남에게 보여줄 사용자 표시 프로필 (API-SHARE-2 §{@code inviter}).
     *
     * <p>탈퇴 여부로 거르지 않는다 — 탈퇴 시 이름·이미지가 이미 파기돼 두 값 모두 {@code null}로
     * 나오므로, 명세가 허용하는 {@code null} 응답과 결과가 같다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserDisplayProfile> findDisplayProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserDisplayProfile(user.getDisplayName(), user.getProfileImageUrl()));
    }
}
