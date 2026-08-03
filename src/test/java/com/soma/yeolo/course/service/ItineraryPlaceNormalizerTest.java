package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.client.NormalizedPlace;
import com.soma.yeolo.course.client.PlaceNormalizer;
import com.soma.yeolo.course.client.StubPlaceNormalizer;
import org.junit.jupiter.api.Test;

class ItineraryPlaceNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void AI_stop에_내부_placeId와_좌표를_주입한다() throws Exception {
        PlaceNormalizer normalizer = (placeName, category, city, country) ->
                new NormalizedPlace("place_test", 33.5, 126.6);
        ItineraryPlaceNormalizer subject = new ItineraryPlaceNormalizer(normalizer);

        JsonNode course = subject.normalize(courseWithStop());

        JsonNode stop = course.path("itinerary").path("days").get(0).path("stops").get(0);
        assertThat(stop.path("placeId").asText()).isEqualTo("place_test");
        assertThat(stop.path("latitude").asDouble()).isEqualTo(33.5);
        assertThat(stop.path("longitude").asDouble()).isEqualTo(126.6);
    }

    @Test
    void 좌표가_없으면_placeId만_주입하고_좌표는_남기지_않는다() throws Exception {
        // 스텁: placeName 기준 결정적 placeId만, 좌표는 없음
        ItineraryPlaceNormalizer subject = new ItineraryPlaceNormalizer(new StubPlaceNormalizer());

        JsonNode course = subject.normalize(courseWithStop());

        JsonNode stop = course.path("itinerary").path("days").get(0).path("stops").get(0);
        assertThat(stop.path("placeId").asText()).startsWith("place_");
        assertThat(stop.has("latitude")).isFalse();
        assertThat(stop.has("longitude")).isFalse();
    }

    @Test
    void placeName이_없거나_itinerary가_비어도_실패하지_않는다() throws Exception {
        ItineraryPlaceNormalizer subject = new ItineraryPlaceNormalizer(new StubPlaceNormalizer());

        JsonNode noStops = MAPPER.readTree("""
                {"destinationCity":"제주","itinerary":{"days":[{"day":1,"stops":[{"sequence":1}]}]}}
                """);

        JsonNode result = subject.normalize(noStops);

        JsonNode stop = result.path("itinerary").path("days").get(0).path("stops").get(0);
        assertThat(stop.has("placeId")).isFalse();
    }

    @Test
    void 정규화가_실패해도_원본_stop을_유지한다() throws Exception {
        PlaceNormalizer boom = (placeName, category, city, country) -> {
            throw new RuntimeException("geocode down");
        };
        ItineraryPlaceNormalizer subject = new ItineraryPlaceNormalizer(boom);

        JsonNode course = subject.normalize(courseWithStop());

        JsonNode stop = course.path("itinerary").path("days").get(0).path("stops").get(0);
        assertThat(stop.path("placeName").asText()).isEqualTo("함덕 해수욕장");
        assertThat(stop.has("placeId")).isFalse();
    }

    private JsonNode courseWithStop() throws Exception {
        return MAPPER.readTree("""
                {
                  "destinationCountry": "대한민국",
                  "destinationCity": "제주",
                  "itinerary": {"days": [{"day": 1, "stops": [
                    {"sequence": 1, "placeName": "함덕 해수욕장", "category": "beach"}
                  ]}]}
                }
                """);
    }
}
