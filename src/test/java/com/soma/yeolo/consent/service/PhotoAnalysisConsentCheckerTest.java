package com.soma.yeolo.consent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 검증 포트의 기본 구현(REQ-8 차단 규칙)만 순수하게 검증한다. */
class PhotoAnalysisConsentCheckerTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void 미동의_사용자는_403으로_차단한다() {
        PhotoAnalysisConsentChecker checker = id -> false;

        assertThatThrownBy(() -> checker.requireAgreed(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PHOTO_CONSENT_REQUIRED);
    }

    @Test
    void 동의한_사용자는_통과시킨다() {
        PhotoAnalysisConsentChecker checker = id -> true;

        assertThatCode(() -> checker.requireAgreed(userId)).doesNotThrowAnyException();
    }
}
