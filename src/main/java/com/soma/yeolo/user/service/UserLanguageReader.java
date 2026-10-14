package com.soma.yeolo.user.service;

import java.util.UUID;

/**
 * 사용자의 선호 언어를 읽는 포트. AI 내부 API(API-AI-1/2)의 {@code language} 필드와
 * {@code Accept-Language} 헤더에 실린다.
 */
@FunctionalInterface
public interface UserLanguageReader {

    /** 저장된 선호 언어. 설정이 없거나 사용자를 못 찾으면 서비스 기본 언어를 돌려준다(항상 지원 코드). */
    String findLanguage(UUID userId);
}
