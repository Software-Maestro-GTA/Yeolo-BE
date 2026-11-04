package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 장소 → 내부 장소 등록, 그리고 그 역방향(내부 placeId → Google Place ID) 포트 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>코스 생성(DOM-2)처럼 <b>다른 도메인</b>이 "AI가 준 장소를 내부 placeId로 바꿔달라"고만 요청하면
 * 되는 경우를 위해 좁은 인터페이스로 노출한다. 소비자는 영속 계층을 모른 채 이 포트에만 의존한다
 * (docs/architecture.md §1-2, §8 — {@code PhotoAnalysisConsentChecker}와 같은 방식).
 *
 * <p>장소 정보는 AI 가 통째로 준다(API-AI-2: stop의 {@code place}에 Google Place ID·이름·분류·좌표). BE 는
 * <b>코스 생성에서 외부 지도 API 를 부르지 않는다</b>(2026-11-04 결정) — 장소명으로 provider 를 조회하던
 * 폴백({@code resolve(PlaceQuery)})은 제거됐다. AI 가 식별자·좌표를 빠뜨린 stop 은 등록하지 않고 그대로 둔다.
 */
public interface PlaceRegistry {

    /**
     * 이미 확보된 장소 정보를 내부 장소로 등록하고 그 결과를 반환한다.
     * provider 식별자가 같은 장소가 이미 있으면 기존 것을 재사용한다(외부 조회 없음).
     */
    SavedPlace register(Place place);

    /**
     * 내부 {@code placeId}들을 Google Place ID 로 되돌린다. 코스 상세 조회(API-COURSE-2)가 stop의
     * {@code googlePlaceId}를 FE가 Google Maps 에 넘길 수 있는 값으로 바꾸는 데 쓴다.
     *
     * @return 내부 식별자 → Google Place ID. 저장돼 있지 않거나 Google 형식이 아닌 provider 식별자
     *         ({@code osm:}·{@code stub:} — 폴백 시절에 등록된 행.
     *         {@link com.soma.yeolo.place.domain.Place#toGooglePlaceId})로 등록된 장소는 결과에서 빠진다.
     */
    Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds);
}
