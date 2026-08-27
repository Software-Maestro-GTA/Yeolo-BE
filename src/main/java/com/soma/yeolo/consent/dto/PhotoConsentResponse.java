package com.soma.yeolo.consent.dto;

import com.soma.yeolo.consent.entity.PhotoAnalysisConsent;
import java.time.Instant;

/**
 * 사진 데이터 분석 동의 저장 응답 (API-PREF-2). 명세상 {@code data}는 {@code consent} 객체를
 * 한 겹 감싼 형태다.
 */
public record PhotoConsentResponse(Consent consent) {

    /** {@code agreedAt}은 Jackson 기본 설정(Boot)에 따라 ISO-8601 문자열로 직렬화된다. */
    public record Consent(boolean agreed, Instant agreedAt, String consentVersion) {
    }

    public static PhotoConsentResponse from(PhotoAnalysisConsent consent) {
        return new PhotoConsentResponse(new Consent(
                consent.isAgreed(), consent.getAgreedAt(), consent.getConsentVersion()));
    }
}
