package com.soma.yeolo.consent.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.util.UUID;

/**
 * 사진 데이터 분석 동의 여부 검증 포트 (REQ-8).
 *
 * <p>사진 기반 취향 분석(API-PREF-3)처럼 <b>다른 도메인</b>이 동의 여부만 확인하면 되는 경우를 위해
 * 좁은 인터페이스로 노출한다. 소비자는 동의 저장(`save`)에 손댈 수 없고 영속 계층도 모른 채 이
 * 포트에만 의존하므로, 단위 테스트에서 람다 하나로 대체할 수 있다 (docs/architecture.md 1-2, 8).
 */
@FunctionalInterface
public interface PhotoAnalysisConsentChecker {

    /** 사용자의 최신 이력이 동의 상태인지 여부. 동의 기록이 아예 없으면 {@code false}. */
    boolean hasAgreed(UUID userId);

    /**
     * 동의하지 않은 사용자를 차단한다 (REQ-8 인수 기준: "동의하지 않으면 분석을 진행하지 않는다").
     * 동의 이력이 없거나 최신 이력이 철회면 403을 던져 분석 파이프라인 자체를 시작하지 않는다.
     */
    default void requireAgreed(UUID userId) {
        if (!hasAgreed(userId)) {
            throw new BusinessException(ErrorCode.PHOTO_CONSENT_REQUIRED);
        }
    }
}
