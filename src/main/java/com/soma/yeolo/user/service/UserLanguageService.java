package com.soma.yeolo.user.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.i18n.SupportedLanguages;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 언어 설정 조회·변경 (API-USER-3/4). 설정은 {@code users.language}에 있고 기본값은 서비스 기본 언어다.
 *
 * <p>변경은 요청 본문의 {@code language}로만 결정한다 — {@code Accept-Language} 헤더는 응답 메시지
 * 언어에만 관여하고 계정 설정을 바꾸지 않는다(명세). 같은 값으로 반복해도 결과는 같다.
 */
@Service
@RequiredArgsConstructor
public class UserLanguageService implements UserLanguageReader {

    private final UserRepository userRepository;
    private final SupportedLanguages languages;

    @Override
    @Transactional(readOnly = true)
    public String findLanguage(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getLanguage)              // 설정이 없으면 null
                .flatMap(languages::normalize)       // 저장값이 지원 목록에서 빠졌으면(설정 축소) 기본값으로
                .orElse(languages.fallback());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findStoredLanguage(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getLanguage)              // 설정이 없으면 null → 빈 값
                .flatMap(languages::normalize);      // 저장값이 지원 목록에서 빠졌으면(설정 축소) 빈 값
    }

    /** 지원하지 않는 코드는 400 으로 거절하고 기존 설정을 유지한다(명세). */
    @Transactional
    public String updateLanguage(UUID userId, String language) {
        String code = languages.normalize(language)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_LANGUAGE));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateLanguage(code);
        return code;
    }
}
