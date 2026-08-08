package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.client.ProfileImageProperties;
import com.soma.yeolo.user.client.ProfileImageStorage;
import com.soma.yeolo.user.domain.ProfileImage;
import com.soma.yeolo.user.dto.UserProfileUpdateRequest;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 부분 수정 규칙(API-USER-1 인수 기준 "nullable 정책에 맞게 처리된다")과 409/413/415 분기.
 * 저장소는 손수 짠 fake, 리포지토리는 {@code JpaRepository}라 목으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final long MAX_BYTES = 1_024;

    /** 이미지 저장 포트 fake: 실제 업로드 없이 호출 사실과 URL만 기록한다. */
    private static final class FakeProfileImageStorage implements ProfileImageStorage {
        private ProfileImage stored;

        @Override
        public String store(UUID userId, ProfileImage image) {
            stored = image;
            return "https://cdn.test/%s.%s".formatted(userId, image.format().getExtension());
        }

        @Override
        public void deleteAll(UUID userId) {
            stored = null;
        }
    }

    @Mock
    private UserRepository userRepository;

    private final FakeProfileImageStorage storage = new FakeProfileImageStorage();

    private UserProfileService service;
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, storage,
                new ProfileImageProperties("stub", MAX_BYTES));
        user = User.createOAuthUser(Provider.GOOGLE, "sub-1", "old@gmail.com", "옛이름", "http://old");
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private MultipartFile pngFile() {
        byte[] png = new byte[64];
        System.arraycopy(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                0, png, 0, 8);
        return new MockMultipartFile("profileImage", "me.png", "image/png", png);
    }

    @Test
    void 전달한_항목만_바뀌고_나머지는_유지된다() {
        service.updateProfile(userId, new UserProfileUpdateRequest(null, "새이름", null));

        assertThat(user.getDisplayName()).isEqualTo("새이름");
        assertThat(user.getEmail()).isEqualTo("old@gmail.com");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://old");
    }

    /** 폼이 빈 파트를 보내는 경우가 흔하다 — 빈 문자열을 "지움"으로 해석하면 값이 사라진다. */
    @Test
    void 공백뿐인_값은_미전송과_같이_취급한다() {
        service.updateProfile(userId, new UserProfileUpdateRequest("   ", "  ", null));

        assertThat(user.getEmail()).isEqualTo("old@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("옛이름");
    }

    @Test
    void 앞뒤_공백은_제거하고_저장한다() {
        when(userRepository.existsByEmailAndDeletedAtIsNullAndIdNot(anyString(), any()))
                .thenReturn(false);

        service.updateProfile(userId, new UserProfileUpdateRequest(" new@gmail.com ", " 새이름 ", null));

        assertThat(user.getEmail()).isEqualTo("new@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("새이름");
    }

    @Test
    void 이미지를_올리면_저장소_URL로_갱신한다() {
        service.updateProfile(userId, new UserProfileUpdateRequest(null, null, pngFile()));

        assertThat(storage.stored).isNotNull();
        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.test/%s.png".formatted(userId));
    }

    @Test
    void 빈_파일_파트는_업로드하지_않고_기존_이미지를_유지한다() {
        MultipartFile empty = new MockMultipartFile("profileImage", "", "image/png", new byte[0]);

        service.updateProfile(userId, new UserProfileUpdateRequest(null, "새이름", empty));

        assertThat(storage.stored).isNull();
        assertThat(user.getProfileImageUrl()).isEqualTo("http://old");
    }

    @Test
    void 다른_사용자가_쓰는_이메일이면_409다() {
        when(userRepository.existsByEmailAndDeletedAtIsNullAndIdNot("taken@gmail.com", userId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(
                userId, new UserProfileUpdateRequest("taken@gmail.com", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_IN_USE);

        assertThat(user.getEmail()).isEqualTo("old@gmail.com");
    }

    /** 같은 이메일을 그대로 다시 보내는 것은 중복이 아니다(자기 자신은 조회에서 제외). */
    @Test
    void 자기_이메일을_그대로_보내면_통과한다() {
        when(userRepository.existsByEmailAndDeletedAtIsNullAndIdNot("old@gmail.com", userId))
                .thenReturn(false);

        service.updateProfile(userId, new UserProfileUpdateRequest("old@gmail.com", null, null));

        assertThat(user.getEmail()).isEqualTo("old@gmail.com");
    }

    @Test
    void 한도를_넘는_파일은_413이고_저장소를_호출하지_않는다() {
        MultipartFile tooLarge = new MockMultipartFile(
                "profileImage", "big.png", "image/png", new byte[(int) MAX_BYTES + 1]);

        assertThatThrownBy(() -> service.updateProfile(
                userId, new UserProfileUpdateRequest(null, null, tooLarge)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_IMAGE_TOO_LARGE);

        assertThat(storage.stored).isNull();
    }

    /** Content-Type을 image/png로 속여도 실제 바이트로 판정하므로 415다. */
    @Test
    void 이미지가_아닌_파일은_415이고_저장소를_호출하지_않는다() {
        MultipartFile fake = new MockMultipartFile(
                "profileImage", "evil.png", "image/png", "#!/bin/sh\nrm -rf /".getBytes());

        assertThatThrownBy(() -> service.updateProfile(
                userId, new UserProfileUpdateRequest(null, null, fake)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);

        assertThat(storage.stored).isNull();
    }

    @Test
    void 존재하지_않는_사용자면_404다() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                unknown, new UserProfileUpdateRequest(null, "새이름", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
