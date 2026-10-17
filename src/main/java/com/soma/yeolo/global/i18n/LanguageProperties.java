package com.soma.yeolo.global.i18n;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 언어 설정 (API-USER-3/4, 2026-10 개정).
 *
 * <p>명세는 "지원 언어 목록 및 기본값은 별도 확정"으로 두었다. 확정 전까지 {@code ko}만 지원하고,
 * 목록은 설정값으로 둬 코드 변경 없이 넓힐 수 있게 한다(예: {@code LANGUAGE_SUPPORTED=ko,en}).
 *
 * <p>목록이 둘인 이유: 사용자가 <b>저장</b>할 수 있는 언어(AI 가 이 언어로 문구를 만든다)와 BE 가
 * 응답 {@code message}를 실제로 <b>렌더링</b>할 수 있는 언어는 다르다. 전자를 넓혀도 번역 리소스가
 * 없으면 후자는 그대로 {@code ko}이고, {@code Content-Language}는 후자만 주장해야 한다.
 *
 * @param supported        저장·AI 전달이 가능한 언어 코드(BCP 47). 요청 본문의 {@code language}는 이 목록과
 *                         (대소문자만 무시하고) 정확히 일치해야 한다
 * @param fallback         저장된 설정이 없을 때의 서비스 기본 언어. {@code supported}에 있어야 한다
 * @param messageLanguages BE 가 응답 메시지를 낼 수 있는 언어. {@code Content-Language}는 이 중에서만 고른다.
 *                         {@code fallback}이 들어 있어야 한다
 */
@ConfigurationProperties(prefix = "language")
public record LanguageProperties(List<String> supported, String fallback, List<String> messageLanguages) {
}
