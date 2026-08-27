package com.soma.yeolo.consent.service;

import com.soma.yeolo.consent.dto.PhotoConsentRequest;
import com.soma.yeolo.consent.entity.PhotoAnalysisConsent;
import com.soma.yeolo.consent.repository.PhotoAnalysisConsentRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 데이터 분석 동의 저장·검증 (API-PREF-2 / FUN-3 / REQ-8).
 *
 * <p>동의·철회를 append-only 이력으로 쌓고(법적 증빙), 현재 동의 여부는 최신 1건으로 판정한다.
 * {@link PhotoAnalysisConsent}는 "엔티티=도메인" 병합형이라 도메인 객체로 그대로 주고받는다
 * (docs/architecture.md 1-1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAnalysisConsentService implements PhotoAnalysisConsentChecker {

    private final PhotoAnalysisConsentRepository consentRepository;

    /**
     * 동의(또는 철회) 이력을 한 건 기록한다 (API-PREF-2).
     * 시각은 클라이언트 값이 아닌 서버 시각으로 찍어 증빙 신뢰성을 확보한다.
     */
    @Transactional
    public PhotoAnalysisConsent save(UUID userId, PhotoConsentRequest request) {
        PhotoAnalysisConsent consent = PhotoAnalysisConsent.record(
                userId, request.agreed(), request.consentVersion(), Instant.now());
        try {
            // flush까지 밀어야 INSERT가 이 try 안에서 일어난다. 그냥 save()면 커밋 시점(메서드 밖)에
            // 터져 아래 catch를 지나치고, 명세의 "동의 저장 실패" 대신 일반 500으로 나간다.
            return consentRepository.saveAndFlush(consent);
        } catch (DataAccessException e) {
            // 명세의 500(동의 저장 실패). 저장 실패를 성공으로 응답하면 증빙이 어긋난다.
            log.error("사진 분석 동의 저장 실패. userId={}", userId, e);
            throw new BusinessException(ErrorCode.PHOTO_CONSENT_SAVE_FAILED, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAgreed(UUID userId) {
        return consentRepository.findTopByUserIdOrderByAgreedAtDescCreatedAtDesc(userId)
                .map(PhotoAnalysisConsent::isAgreed)
                .orElse(false);
    }
}
