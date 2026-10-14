package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.i18n.LanguageProperties;
import com.soma.yeolo.global.i18n.SupportedLanguages;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** API-USER-3/4 — 조회는 저장값 또는 기본값, 변경은 지원 코드만 받고 교체한다. */
@ExtendWith(MockitoExtension.class)
class UserLanguageServiceTest {

    @Mock
    private UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();

    private UserLanguageService service(String... supported) {
        return new UserLanguageService(userRepository,
                new SupportedLanguages(new LanguageProperties(List.of(supported), "ko", List.of("ko"))));
    }

    private User user() {
        return User.createOAuthUser(Provider.GOOGLE, "google-1", "a@b.c", "이름", null);
    }

    /** API-USER-3: 설정이 없으면(열이 null) 서비스 기본 언어 — 기본값의 출처는 설정 하나다. */
    @Test
    void 설정이_없으면_기본_언어를_돌려준다() {
        User user = user();
        assertThat(user.getLanguage()).isNull();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service("ko", "en").findLanguage(userId)).isEqualTo("ko");
    }

    @Test
    void 저장된_언어를_돌려준다() {
        User user = user();
        user.updateLanguage("en");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service("ko", "en").findLanguage(userId)).isEqualTo("en");
    }

    /** 설정이 좁아져 저장값이 지원 목록에서 빠졌으면 기본값으로 읽는다 — AI 에 미지원 코드를 보내지 않는다. */
    @Test
    void 저장값이_지원_목록에_없으면_기본_언어로_읽는다() {
        User user = user();
        user.updateLanguage("en");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service("ko").findLanguage(userId)).isEqualTo("ko");
    }

    @Test
    void 사용자를_못_찾아도_읽기는_기본_언어다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service("ko").findLanguage(userId)).isEqualTo("ko");
    }

    @Test
    void 지원_코드로_변경하면_소문자로_정리해_저장하고_돌려준다() {
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service("ko", "en").updateLanguage(userId, "EN")).isEqualTo("en");
        assertThat(user.getLanguage()).isEqualTo("en");
    }

    /** 지역 코드 매칭은 명세 미확정 — "en-US" 를 "en" 으로 고쳐 저장하지 않고 400 이다. */
    @Test
    void 지역_코드가_붙은_태그는_400이다() {
        assertThatThrownBy(() -> service("ko", "en").updateLanguage(userId, "en-US"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_LANGUAGE);
        verifyNoInteractions(userRepository);
    }

    @Test
    void 같은_값으로_반복_변경해도_결과는_같다() {
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserLanguageService service = service("ko", "en");

        service.updateLanguage(userId, "en");
        assertThat(service.updateLanguage(userId, "en")).isEqualTo("en");
        assertThat(user.getLanguage()).isEqualTo("en");
    }

    /** 명세: 지원하지 않는 코드는 400 으로 거절하고 기존 설정을 유지한다. 조회조차 하지 않는다. */
    @Test
    void 지원하지_않는_코드는_400이고_설정은_유지된다() {
        assertThatThrownBy(() -> service("ko").updateLanguage(userId, "ja"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_LANGUAGE);
        verifyNoInteractions(userRepository);
    }

    @Test
    void 변경_대상_사용자가_없으면_404다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("ko").updateLanguage(userId, "ko"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
