package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.Optional;

/**
 * 장소명 → 내부 장소 정규화 포트 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>코스 생성(DOM-2)처럼 <b>다른 도메인</b>이 "AI가 준 장소명을 내부 placeId·좌표로 바꿔달라"고만
 * 요청하면 되는 경우를 위해 좁은 인터페이스로 노출한다. 소비자는 provider 조회·영속 계층을 모른 채
 * 이 포트에만 의존하므로, 단위 테스트에서 람다 하나로 대체할 수 있다
 * (docs/architecture.md §1-2, §8 — {@code PhotoAnalysisConsentChecker}와 같은 방식).
 */
@FunctionalInterface
public interface PlaceRegistry {

    /**
     * 장소를 조회해 내부 장소로 등록하고 그 결과를 반환한다. 이미 등록된 장소면 기존 것을 재사용한다.
     *
     * @return 정규화된 장소. provider가 장소를 찾지 못했거나 조회에 실패하면 빈 값.
     */
    Optional<SavedPlace> resolve(PlaceQuery query);
}
