package com.soma.yeolo.course.service;

import static com.soma.yeolo.global.client.JsonNodes.number;
import static com.soma.yeolo.global.client.JsonNodes.text;
import static com.soma.yeolo.global.client.JsonNodes.textList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 코스의 방문지를 내부 장소로 정규화한다. (DOM-3 §"장소 정보 처리 기준")
 *
 * <p>AI는 stop마다 {@code place} 객체로 장소 정보를 통째로 준다(API-AI-2: provider 장소 식별자·
 * 좌표·주소·평점·사진·운영시간). 그 {@code placeId}는 <b>Google Place ID 같은 외부 식별자</b>라
 * 앱에 노출할 수 없으므로(DOM-3), 저장 직전에 각 장소를 내부 장소로 등록하고
 * {@code place.placeId}를 <b>내부 식별자(UUID)로 바꿔 넣는다</b>. 이 치환이 이 클래스의 핵심이다 —
 * 그래야 앱이 장소 상세(API-PLACE-1)로 이동할 수 있다.
 *
 * <p>AI가 장소 정보를 온전히 주지 못한 경우(식별자·좌표 누락)에만 장소명으로 외부 provider를 조회하는
 * 폴백을 탄다. 명세 개정 전까지는 이쪽이 정상 경로였다.
 *
 * <p><b>정규화 실패는 코스 생성을 실패시키지 않는다.</b> AI 생성(수십 초)이 이미 끝난 뒤의 부가
 * 단계이므로, 장소 한 곳을 못 찾았다고 코스 전체를 버리지 않는다. 대신 그 stop의
 * {@code place.placeId}를 <b>제거</b>해 정규화되지 않은 장소를 명확히 남긴다 — AI가 넣어 보낸 외부
 * 식별자가 FE 응답으로 새어 나가지 않게 하는 안전장치이기도 하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPlaceNormalizer {

    private final PlaceRegistry placeRegistry;

    /** 코스 JSON의 모든 stop을 제자리에서 정규화한다. */
    public void normalize(JsonNode course, TripCondition condition) {
        // 한 코스 안에서 같은 장소가 여러 번 등장하는 일은 흔하다(숙소가 매일 첫/마지막 stop 등).
        // 코스 단위로 결과를 재사용해 같은 장소를 반복 등록·조회하지 않는다.
        Map<String, Optional<SavedPlace>> resolved = new HashMap<>();
        for (JsonNode day : course.path("itinerary").path("days")) {
            for (JsonNode stop : day.path("stops")) {
                if (stop.path("place") instanceof ObjectNode place) {
                    normalizePlace(place, condition, resolved);
                }
            }
        }
    }

    private void normalizePlace(ObjectNode place, TripCondition condition,
                                Map<String, Optional<SavedPlace>> resolved) {
        // 정규화에 성공할 때만 다시 채운다 — AI가 준 외부 식별자는 그대로 두지 않는다.
        String providerPlaceId = text(place, "placeId");
        place.remove("placeId");

        String placeName = text(place, "placeName");
        if (placeName == null) {
            return;
        }
        try {
            lookUp(place, providerPlaceId, placeName, condition, resolved)
                    .ifPresent(saved -> {
                        place.put("placeId", saved.placeId().toString());
                        place.put("latitude", saved.latitude());
                        place.put("longitude", saved.longitude());
                    });
        } catch (RuntimeException e) {
            // 장소 등록·조회의 예기치 못한 실패. 이 장소만 미정규화로 남기고 코스 생성은 계속한다.
            log.warn("장소 정규화 중 오류 - '{}': {}", placeName, e.toString());
        }
    }

    /**
     * AI가 준 장소를 등록하거나(정상 경로), 정보가 부족하면 장소명으로 조회한다(폴백).
     *
     * <p>캐시 키에 접두사를 붙여 두 경로를 섞지 않는다 — provider 식별자와 검색어는 서로 다른
     * 이름공간이라 우연히 같은 문자열이면 엉뚱한 장소를 재사용하게 된다.
     */
    private Optional<SavedPlace> lookUp(ObjectNode place, String providerPlaceId, String placeName,
                                        TripCondition condition,
                                        Map<String, Optional<SavedPlace>> resolved) {
        Place supplied = toSuppliedPlace(place, providerPlaceId, placeName);
        if (supplied != null) {
            return resolved.computeIfAbsent("id:" + providerPlaceId,
                    key -> Optional.of(placeRegistry.register(supplied)));
        }
        PlaceQuery query = new PlaceQuery(placeName, text(place, "category"),
                condition.destinationCountry(), condition.destinationCity());
        log.info("AI가 장소 정보를 온전히 주지 않아 provider 조회로 폴백한다 - '{}'", placeName);
        return resolved.computeIfAbsent("q:" + query.searchText(), key -> placeRegistry.resolve(query));
    }

    /**
     * AI가 준 {@code place}를 저장용 장소로 옮긴다. 식별자나 좌표가 없으면 저장할 수 없으므로
     * ({@link Place}의 불변식) null을 돌려 폴백을 태운다.
     */
    private Place toSuppliedPlace(ObjectNode place, String providerPlaceId, String placeName) {
        Double latitude = number(place, "latitude");
        Double longitude = number(place, "longitude");
        if (providerPlaceId == null || latitude == null || longitude == null) {
            return null;
        }
        return new Place(
                providerPlaceId,
                placeName,
                text(place, "placeEngName"),
                text(place, "category"),
                text(place, "address"),
                latitude,
                longitude,
                number(place, "rating"),
                text(place, "photoUrl"),
                textList(place, "openingHours")
        );
    }
}
