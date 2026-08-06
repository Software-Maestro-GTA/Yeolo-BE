package com.soma.yeolo.course.service;

import static com.soma.yeolo.global.client.JsonNodes.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.course.domain.TripCondition;
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
 * <p>AI는 장소명과 분류만 준다(API-AI-2). 앱이 지도를 그리고 장소 상세(API-PLACE-1)로 이동하려면
 * 내부 {@code placeId}와 좌표가 필요하므로, 저장 직전에 각 stop을 장소 조회로 정규화해
 * {@code placeId}·{@code latitude}·{@code longitude}를 채워 넣는다.
 *
 * <p><b>정규화 실패는 코스 생성을 실패시키지 않는다.</b> AI 생성(수십 초)이 이미 끝난 뒤의 부가
 * 단계이므로, 장소 한 곳을 못 찾았다고 코스 전체를 버리지 않는다. 대신 그 stop의
 * {@code placeId}를 <b>제거</b>해 정규화되지 않은 stop을 명확히 남긴다 — AI가 임의의 외부 식별자를
 * 넣어 보내더라도 FE 응답으로 새어 나가지 않게 하는 안전장치이기도 하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPlaceNormalizer {

    private final PlaceRegistry placeRegistry;

    /** 코스 JSON의 모든 stop을 제자리에서 정규화한다. */
    public void normalize(JsonNode course, TripCondition condition) {
        // 한 코스 안에서 같은 장소가 여러 번 등장하는 일은 흔하다(숙소가 매일 첫/마지막 stop 등).
        // 코스 단위로 결과를 재사용해 같은 장소를 provider에 반복 조회하지 않는다.
        Map<String, Optional<SavedPlace>> resolved = new HashMap<>();
        for (JsonNode day : course.path("itinerary").path("days")) {
            for (JsonNode stop : day.path("stops")) {
                if (stop instanceof ObjectNode stopNode) {
                    normalizeStop(stopNode, condition, resolved);
                }
            }
        }
    }

    private void normalizeStop(ObjectNode stop, TripCondition condition,
                               Map<String, Optional<SavedPlace>> resolved) {
        // 정규화에 성공할 때만 다시 채운다 — AI가 넣어 보낸 값은 신뢰하지 않는다.
        stop.remove("placeId");
        String placeName = text(stop, "placeName");
        if (placeName == null) {
            return;
        }
        PlaceQuery query = new PlaceQuery(placeName, text(stop, "category"),
                condition.destinationCountry(), condition.destinationCity());
        try {
            resolved.computeIfAbsent(query.searchText(), key -> placeRegistry.resolve(query))
                    .ifPresent(place -> {
                        stop.put("placeId", place.placeId().toString());
                        stop.put("latitude", place.latitude());
                        stop.put("longitude", place.longitude());
                    });
        } catch (RuntimeException e) {
            // 장소 조회·저장의 예기치 못한 실패. 이 stop만 미정규화로 남기고 코스 생성은 계속한다.
            log.warn("장소 정규화 중 오류 - '{}': {}", placeName, e.toString());
        }
    }
}
