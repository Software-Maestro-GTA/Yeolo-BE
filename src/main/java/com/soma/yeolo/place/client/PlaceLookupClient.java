package com.soma.yeolo.place.client;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.Optional;

/**
 * 장소명 → 좌표·주소 등 장소 상세 조회 포트 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>외부 provider(Google Places / OpenStreetMap) 연동부를 격리하기 위한 인터페이스이며,
 * {@code place.provider} 설정으로 구현체를 교체한다. 어댑터는 provider 응답을 순수 도메인
 * {@link Place}로 매핑해 돌려준다({@code ReverseGeocodeClient}가 {@code GeoLocation}을 돌려주는 것과
 * 같은 방식). (docs/architecture.md §5)
 *
 * <p><b>구현 계약 — 예외를 던지지 않는다:</b> 조회 실패(결과 없음·HTTP 오류·파싱 실패)는 모두
 * {@link Optional#empty()}로 돌려준다. 장소 정규화는 이미 완료된 AI 코스 생성(수십 초)의 뒤에
 * 붙는 부가 단계라, 장소 하나를 못 찾았다고 코스 생성 전체를 실패시키지 않기 위해서다.
 */
public interface PlaceLookupClient {

    /**
     * 질의에 가장 잘 맞는 장소 하나를 조회한다.
     *
     * @return 좌표까지 확보한 결과. 결과가 없거나 조회에 실패하면 빈 값.
     */
    Optional<Place> lookup(PlaceQuery query);
}
