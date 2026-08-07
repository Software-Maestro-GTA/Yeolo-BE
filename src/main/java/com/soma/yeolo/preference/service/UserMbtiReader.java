package com.soma.yeolo.preference.service;

import com.soma.yeolo.preference.domain.Mbti;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 MBTI 조회 포트 (API-PREF-1 → API-COURSE-1 / DOM-5).
 *
 * <p>코스 생성(DOM-3)은 "저장된 MBTI를 읽어 AI 요청에 싣는" 것만 필요하므로, 선호 도메인의
 * 저장·갱신 로직까지 끌어오지 않도록 읽기 전용 포트로 좁혀 노출한다. 소비자는 영속 계층을 모른 채
 * 이 포트에만 의존하므로 단위 테스트에서 람다로 대체할 수 있다
 * (docs/architecture.md §1-2 — {@code PhotoAnalysisConsentChecker}와 같은 방식).
 */
@FunctionalInterface
public interface UserMbtiReader {

    /** 사용자가 저장해 둔 MBTI. 입력한 적이 없으면 빈 값. */
    Optional<Mbti> findMbti(UUID userId);
}
