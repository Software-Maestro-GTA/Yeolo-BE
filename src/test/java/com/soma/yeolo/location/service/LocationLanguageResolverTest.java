package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.i18n.LanguageProperties;
import com.soma.yeolo.global.i18n.SupportedLanguages;
import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.user.service.UserLanguageReader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 표시명 언어: 저장된 언어 설정 → Accept-Language → 기본 언어. */
@ExtendWith(MockitoExtension.class)
class LocationLanguageResolverTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private UserLanguageReader userLanguageReader;

    private LocationLanguageResolver resolver() {
        return new LocationLanguageResolver(
                new SupportedLanguages(new LanguageProperties(List.of("ko", "en"), "ko", List.of("ko"))),
                userLanguageReader);
    }

    /** 앱 설정(API-USER-4)이 사용자가 고른 언어다 — 기기 로케일 헤더가 달라도 설정이 이긴다. */
    @Test
    void 로그인_사용자의_저장된_언어_설정이_헤더보다_우선한다() {
        when(userLanguageReader.findStoredLanguage(USER)).thenReturn(Optional.of("en"));

        assertThat(resolver().resolve("ko-KR,ko;q=0.9", USER)).isEqualTo(LocationLanguage.EN);
        assertThat(resolver().resolve(null, USER)).isEqualTo(LocationLanguage.EN);
    }

    /** 설정을 하지 않은 사용자(열이 null)는 헤더로 넘어간다 — 기본 언어로 메우면 헤더의 영어가 무시된다. */
    @Test
    void 설정이_없는_사용자는_헤더로_정한다() {
        when(userLanguageReader.findStoredLanguage(USER)).thenReturn(Optional.empty());

        assertThat(resolver().resolve("en-US,en;q=0.9,ko;q=0.5", USER)).isEqualTo(LocationLanguage.EN);
        assertThat(resolver().resolve("ko", USER)).isEqualTo(LocationLanguage.KO);
    }

    @Test
    void 비로그인_요청은_헤더로_정하고_설정을_읽지_않는다() {
        assertThat(resolver().resolve("en-GB", null)).isEqualTo(LocationLanguage.EN);
        assertThat(resolver().resolve("ko-KR,ko;q=0.9,en;q=0.8", null)).isEqualTo(LocationLanguage.KO);
        verifyNoInteractions(userLanguageReader);
    }

    /** 명세 §언어 정책: 헤더가 없거나 지원 언어가 매칭되지 않으면 서비스 기본 언어. 400 이 아니다. */
    @Test
    void 설정도_헤더_매칭도_없으면_기본_언어다() {
        when(userLanguageReader.findStoredLanguage(USER)).thenReturn(Optional.empty());

        assertThat(resolver().resolve(null, USER)).isEqualTo(LocationLanguage.KO);
        assertThat(resolver().resolve("ja", null)).isEqualTo(LocationLanguage.KO);
        assertThat(resolver().resolve(";;;=", null)).isEqualTo(LocationLanguage.KO);
    }

    /** 설정이 데이터셋에 없는 언어로 넓혀져도(예: ja) 표시명은 한국어로 떨어진다. */
    @Test
    void 표시명이_없는_저장_언어는_한국어다() {
        when(userLanguageReader.findStoredLanguage(USER)).thenReturn(Optional.of("ja"));

        assertThat(resolver().resolve("en", USER)).isEqualTo(LocationLanguage.KO);
    }
}
