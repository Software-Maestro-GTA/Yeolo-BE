package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.Optional;

/**
 * 장소 → 내부 장소 정규화 포트 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>코스 생성(DOM-2)처럼 <b>다른 도메인</b>이 "AI가 준 장소를 내부 placeId로 바꿔달라"고만 요청하면
 * 되는 경우를 위해 좁은 인터페이스로 노출한다. 소비자는 provider 조회·영속 계층을 모른 채 이 포트에만
 * 의존한다 (docs/architecture.md §1-2, §8 — {@code PhotoAnalysisConsentChecker}와 같은 방식).
 *
 * <p>진입점이 둘인 이유는 <b>명세 개정으로 AI가 장소 정보를 통째로 주기 시작했기 때문이다</b>
 * (API-AI-2: stop의 {@code place}에 provider 식별자·좌표·주소·평점까지 포함). 이제 BE가 외부
 * provider를 다시 조회할 이유가 없으므로 {@link #register}가 정상 경로이고, {@link #resolve}는
 * AI가 장소 정보를 온전히 주지 못했을 때를 위한 폴백이다.
 */
public interface PlaceRegistry {

    /**
     * 이미 확보된 장소 정보를 내부 장소로 등록하고 그 결과를 반환한다.
     * provider 식별자가 같은 장소가 이미 있으면 기존 것을 재사용한다(외부 조회 없음).
     */
    SavedPlace register(Place place);

    /**
     * 장소명으로 외부 provider를 조회해 내부 장소로 등록하고 그 결과를 반환한다.
     * 이미 등록된 장소면 기존 것을 재사용한다.
     *
     * @return 정규화된 장소. provider가 장소를 찾지 못했거나 조회에 실패하면 빈 값.
     */
    Optional<SavedPlace> resolve(PlaceQuery query);
}
