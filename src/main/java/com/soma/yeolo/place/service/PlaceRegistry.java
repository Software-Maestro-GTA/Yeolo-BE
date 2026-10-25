package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소 → 내부 장소 정규화, 그리고 그 역방향(내부 placeId → Google Place ID) 포트 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>코스 생성(DOM-2)처럼 <b>다른 도메인</b>이 "AI가 준 장소를 내부 placeId로 바꿔달라"고만 요청하면
 * 되는 경우를 위해 좁은 인터페이스로 노출한다. 소비자는 provider 조회·영속 계층을 모른 채 이 포트에만
 * 의존한다 (docs/architecture.md §1-2, §8 — {@code PhotoAnalysisConsentChecker}와 같은 방식).
 *
 * <p>진입점이 둘인 이유는 <b>명세 개정으로 AI가 장소 정보를 통째로 주기 시작했기 때문이다</b>
 * (API-AI-2: stop의 {@code place}에 provider 식별자·이름·분류·좌표. 2026-10 개정 전 AI는 주소·평점·
 * 사진·운영시간까지 줬고, 전환기에는 아직 올 수 있어 오면 그대로 담는다). 이제 BE가 외부
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

    /**
     * 내부 {@code placeId}들을 Google Place ID 로 되돌린다. 코스 상세 조회(API-COURSE-2)가 stop의
     * {@code placeId}를 FE가 Google Maps 에 넘길 수 있는 값으로 바꾸는 데 쓴다.
     *
     * @return 내부 식별자 → Google Place ID. 저장돼 있지 않거나 Google 형식이 아닌 provider 식별자
     *         ({@code osm:}·{@code stub:} — {@link com.soma.yeolo.place.domain.Place#toGooglePlaceId})로
     *         등록된 장소는 결과에서 빠진다.
     */
    Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds);
}
