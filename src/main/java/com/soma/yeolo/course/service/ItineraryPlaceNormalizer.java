package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.course.client.NormalizedPlace;
import com.soma.yeolo.course.client.PlaceNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 코스 응답의 각 방문지({@code itinerary.days[].stops[]})를 {@link PlaceNormalizer}로 정규화해
 * 내부 {@code placeId}와 좌표({@code latitude}/{@code longitude})를 주입한다. (DOM-3 장소 정보 처리 기준)
 *
 * <p>AI 응답에는 {@code placeId}·좌표가 없고 {@code placeName}·{@code category} 중심으로 오므로,
 * BE가 장소 조회/지오코딩 결과를 채워 저장 이후 상세 조회에서 좌표·내부 식별자를 제공할 수 있게 한다.
 * 정규화는 <b>부가</b> 단계이므로 어떤 실패도 코스 생성을 막지 않는다 — 개별 stop 처리 실패는 로깅 후
 * 원본 그대로 두고 다음 stop으로 진행한다. (AI 응답에 placeId가 없어도 저장/후처리는 실패하지 않는다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPlaceNormalizer {

    private final PlaceNormalizer placeNormalizer;

    /**
     * AI가 산출한 {@code course} 노드의 방문지를 정규화(내부 placeId·좌표 주입)해 반환한다. 입력 노드를
     * 제자리에서 보강하며, 여행 도시·국가는 코스 노드의 {@code destinationCity}/{@code destinationCountry}를
     * 지오코딩 맥락으로 쓴다.
     */
    public JsonNode normalize(JsonNode course) {
        if (course == null || !course.isObject()) {
            return course;
        }
        String city = text(course, "destinationCity");
        String country = text(course, "destinationCountry");

        JsonNode days = course.path("itinerary").path("days");
        if (!days.isArray()) {
            return course;
        }
        for (JsonNode day : days) {
            JsonNode stops = day.path("stops");
            if (!stops.isArray()) {
                continue;
            }
            for (JsonNode stop : stops) {
                if (stop instanceof ObjectNode objectStop) {
                    normalizeStop(objectStop, city, country);
                }
            }
        }
        return course;
    }

    /** 한 방문지에 내부 placeId·좌표를 주입한다. 실패해도 예외를 던지지 않고 원본을 유지한다. */
    private void normalizeStop(ObjectNode stop, String city, String country) {
        String placeName = text(stop, "placeName");
        if (placeName == null) {
            return;
        }
        try {
            NormalizedPlace place = placeNormalizer.normalize(placeName, text(stop, "category"), city, country);
            if (place == null) {
                return;
            }
            if (place.placeId() != null) {
                stop.put("placeId", place.placeId());
            }
            if (place.latitude() != null) {
                stop.put("latitude", place.latitude());
            }
            if (place.longitude() != null) {
                stop.put("longitude", place.longitude());
            }
        } catch (Exception e) {
            log.warn("Place normalize failed for stop '{}': {}", placeName, e.toString());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }
}
