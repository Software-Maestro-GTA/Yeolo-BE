package com.soma.yeolo.user.service;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자의 선호 언어를 읽는 포트 (API-USER-3/4 의 저장값). AI 내부 API(API-AI-1/2)의 {@code language} 필드와
 * {@code Accept-Language} 헤더, 그리고 국가·도시 표시명 언어(API-LOC-1/2/3, {@code LocationLanguageResolver})가 쓴다.
 */
public interface UserLanguageReader {

    /** 저장된 선호 언어. 설정이 없거나 사용자를 못 찾으면 서비스 기본 언어를 돌려준다(항상 지원 코드). */
    String findLanguage(UUID userId);

    /**
     * 사용자가 <b>직접 저장한</b> 선호 언어. 설정이 없거나, 사용자를 못 찾거나, 저장값이 지원 목록에서 빠졌으면
     * (설정 축소) 빈 값 — {@link #findLanguage}와 달리 기본 언어로 메우지 않는다. "설정을 안 했다"와 "기본 언어를
     * 골랐다"를 가려야 하는 곳(표시명 언어: 설정이 없으면 요청 헤더로 넘어간다)이 쓴다.
     */
    Optional<String> findStoredLanguage(UUID userId);
}
