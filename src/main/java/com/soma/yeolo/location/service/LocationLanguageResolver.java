package com.soma.yeolo.location.service;

import com.soma.yeolo.global.i18n.SupportedLanguages;
import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.user.service.UserLanguageReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국가·도시 표시명 언어 결정 (API-LOC-1/2/3 §언어 정책 + API-USER-3/4 의 저장된 선호 언어).
 *
 * <p>순서는 <b>저장된 언어 설정 → {@code Accept-Language} → 서비스 기본 언어</b>다.
 * <ol>
 *   <li>로그인한 사용자가 API-USER-4 로 언어를 <b>직접 저장</b>했으면 그 값이다. 앱의 언어 설정이 곧
 *       사용자가 고른 언어이므로, 기기 로케일에서 온 헤더가 달라도 설정이 이긴다. 설정하지 않은 사용자
 *       ({@code users.language} null)와 비로그인 요청은 다음 단계로 간다 — 그래서 {@link UserLanguageReader#findLanguage}
 *       (기본 언어로 메움)가 아니라 {@link UserLanguageReader#findStoredLanguage}를 쓴다.</li>
 *   <li>{@code Accept-Language}에서 데이터셋이 표시명을 가진 언어({@link LocationLanguage#codes()})를 우선순위대로
 *       고른다(명세 §언어 정책). 비로그인 화면(공유 미리보기 전, 로그인 전 둘러보기)은 이 단계로 정해진다.</li>
 *   <li>둘 다 없으면 서비스 기본 언어({@code language.fallback}).</li>
 * </ol>
 *
 * <p>API-USER-3/4 의 메시지 언어 정책은 헤더를 저장값보다 앞세우지만, 그것은 응답 {@code message}(번역 리소스가
 * 있는 언어만) 이야기다. 표시명은 사용자가 설정 화면에서 고른 언어대로 보여야 한다는 요구(2026-10-19)를 따른다.
 * 지역 API 는 인증이 필요 없지만 토큰이 오면 {@code JwtAuthenticationFilter}가 사용자를 식별하므로 1단계가 된다.
 */
@Component
@RequiredArgsConstructor
public class LocationLanguageResolver {

    private final SupportedLanguages languages;
    private final UserLanguageReader userLanguageReader;

    /**
     * @param acceptLanguage 요청의 {@code Accept-Language}. 없으면 null
     * @param userId         인증된 사용자. 비로그인이면 null
     */
    public LocationLanguage resolve(String acceptLanguage, UUID userId) {
        if (userId != null) {
            var stored = userLanguageReader.findStoredLanguage(userId);
            if (stored.isPresent()) {
                return LocationLanguage.of(stored.get());
            }
        }
        return languages.matchContentLanguage(acceptLanguage, LocationLanguage.codes())
                .map(LocationLanguage::of)
                .orElseGet(() -> LocationLanguage.of(languages.fallback()));
    }
}
