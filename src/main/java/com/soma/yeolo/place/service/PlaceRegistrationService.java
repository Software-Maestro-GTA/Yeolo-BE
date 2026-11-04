package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.port.PlaceRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소 등록 (DOM-3 §"장소 정보 처리 기준").
 *
 * <p>AI가 준 장소(API-AI-2의 stop {@code place})를 내부 장소로 저장하고 내부 {@code placeId}를
 * 부여한다. 2026-10 개정 후 AI는 식별자·이름·분류·좌표만 주므로 그 외 상세는 비어 있을 수 있다.
 * 같은 장소는 provider 식별자 기준으로 한 번만 저장된다. 장소명으로 외부 provider 를 조회해 채우던
 * 폴백은 2026-11-04 에 제거됐다 — BE 는 코스 생성에서 지도 API 를 부르지 않는다.
 *
 * <p><b>캐시 갱신 정책:</b> 한 번 저장한 장소는 다시 저장하지 않고 기존 행을 그대로 돌려준다
 * (평점·운영시간·분류는 변할 수 있지만 갱신하지 않는다). 주기적 갱신·TTL은 운영 데이터를 보고 정할
 * 사항이라 이번 범위에 넣지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceRegistrationService implements PlaceRegistry {

    private final PlaceRepository placeRepository;

    /**
     * AI가 준 장소를 그대로 등록한다. 외부 조회가 없으므로 실패 경로가 없다.
     *
     * <p>이미 저장된 장소면 <b>기존 행을 그대로 돌려준다</b> — AI가 같은 장소에 다른 값(갱신된 평점·
     * 사진)을 실어 보내도 덮어쓰지 않는다(캐시 갱신 정책은 위 문서 참고).
     */
    @Override
    public SavedPlace register(Place place) {
        return placeRepository.saveIfAbsent(place);
    }

    /** Google 형식이 아닌 provider 식별자는 걸러낸다 — FE가 Google Maps 에 넘겨도 찾을 수 없는 값이다. */
    @Override
    public Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds) {
        Map<UUID, String> result = new HashMap<>();
        placeRepository.findProviderPlaceIds(placeIds).forEach((placeId, providerPlaceId) -> {
            String googlePlaceId = Place.toGooglePlaceId(providerPlaceId);
            if (googlePlaceId != null) {
                result.put(placeId, googlePlaceId);
            } else {
                // 형식이 어긋나 모든 placeId 가 비는 사고를 진단할 수 있게 남긴다(osm 시절 행은 정상적으로 여기 온다).
                log.debug("Google 형식이 아닌 provider 식별자라 placeId 를 비운다. placeId={}, providerPlaceId={}",
                        placeId, providerPlaceId);
            }
        });
        return result;
    }
}
