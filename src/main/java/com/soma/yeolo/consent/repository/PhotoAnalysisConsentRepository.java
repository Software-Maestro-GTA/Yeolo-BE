package com.soma.yeolo.consent.repository;

import com.soma.yeolo.consent.entity.PhotoAnalysisConsent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사진 데이터 분석 동의 이력 저장소. "엔티티=도메인" 병합형이라 별도 포트 없이 Spring Data를
 * 직접 사용한다 (docs/architecture.md 1-2).
 */
public interface PhotoAnalysisConsentRepository extends JpaRepository<PhotoAnalysisConsent, UUID> {

    /**
     * 사용자의 최신 동의 이력 1건. append-only 이력에서 현재 동의 상태를 판정하는 유일한 경로다.
     * 동시에 들어온 두 요청이 같은 {@code agreedAt}을 가질 수 있어 {@code createdAt}을 보조 정렬로 둔다.
     */
    Optional<PhotoAnalysisConsent> findTopByUserIdOrderByAgreedAtDescCreatedAtDesc(UUID userId);
}
