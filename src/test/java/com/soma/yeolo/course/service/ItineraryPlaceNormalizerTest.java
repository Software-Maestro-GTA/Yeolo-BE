package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.BudgetType;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItineraryPlaceNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TripCondition CONDITION = new TripCondition(
            "대한민국", "제주", LocalDate.of(2026, 8, 1), 2, BudgetType.STANDARD);

    private static final String AI_COURSE = """
            {
              "title": "2박 3일 제주 힐링 코스",
              "itinerary": {
                "days": [
                  {
                    "day": 1,
                    "stops": [
                      {"sequence": 1, "placeName": "성산일출봉", "category": "nature"},
                      {"sequence": 2, "placeName": "협재 해수욕장", "category": "beach"}
                    ]
                  }
                ]
              }
            }
            """;

    private JsonNode parse(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private JsonNode stops(JsonNode course) {
        return course.path("itinerary").path("days").get(0).path("stops");
    }

    /** 장소명을 그대로 좌표에 매핑하는 fake 레지스트리. 질의 내용도 함께 기록한다. */
    private static final class FakePlaceRegistry implements PlaceRegistry {
        private final List<PlaceQuery> queries = new ArrayList<>();
        private final List<String> unresolvable;

        private FakePlaceRegistry(String... unresolvable) {
            this.unresolvable = List.of(unresolvable);
        }

        @Override
        public Optional<SavedPlace> resolve(PlaceQuery query) {
            queries.add(query);
            if (unresolvable.contains(query.placeName())) {
                return Optional.empty();
            }
            return Optional.of(new SavedPlace(UUID.randomUUID(), query.placeName(), query.category(),
                    "제주", 33.4581, 126.9425, null, List.of(), List.of()));
        }
    }

    @Test
    void 각_stop에_내부_placeId와_좌표를_채운다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        JsonNode first = stops(course).get(0);
        assertThat(UUID.fromString(first.path("placeId").asText())).isNotNull();
        assertThat(first.path("latitude").asDouble()).isEqualTo(33.4581);
        assertThat(first.path("longitude").asDouble()).isEqualTo(126.9425);
        assertThat(stops(course).get(1).path("placeId").asText()).isNotBlank();
    }

    @Test
    void 목적지를_붙여_동명_장소를_구분한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();

        new ItineraryPlaceNormalizer(registry).normalize(parse(AI_COURSE), CONDITION);

        assertThat(registry.queries).hasSize(2);
        assertThat(registry.queries.get(0).searchText()).isEqualTo("성산일출봉, 제주, 대한민국");
        assertThat(registry.queries.get(0).category()).isEqualTo("nature");
    }

    /** 숙소처럼 한 코스에서 반복 등장하는 장소를 provider에 매번 다시 묻지 않는다. */
    @Test
    void 코스_안에서_같은_장소는_한_번만_조회한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [
                  {"day": 1, "stops": [{"sequence": 1, "placeName": "제주 호텔"}]},
                  {"day": 2, "stops": [{"sequence": 1, "placeName": "제주 호텔"}]}
                ]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(registry.queries).hasSize(1);
        // 두 stop 모두 같은 내부 placeId로 채워진다.
        JsonNode days = course.path("itinerary").path("days");
        assertThat(days.get(1).path("stops").get(0).path("placeId").asText())
                .isEqualTo(days.get(0).path("stops").get(0).path("placeId").asText());
    }

    @Test
    void 정규화하지_못한_stop은_placeId_없이_남는다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry("협재 해수욕장");
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        JsonNode unresolved = stops(course).get(1);
        assertThat(unresolved.has("placeId")).isFalse();
        assertThat(unresolved.has("latitude")).isFalse();
        // 나머지 stop은 정상적으로 정규화된다.
        assertThat(stops(course).get(0).path("placeId").asText()).isNotBlank();
    }

    /** AI가 외부 식별자를 넣어 보내더라도 FE 응답으로 새어 나가면 안 된다 (DOM-3). */
    @Test
    void AI가_보낸_placeId는_신뢰하지_않고_제거한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry("성산일출봉");
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "placeName": "성산일출봉", "placeId": "ChIJ_GOOGLE_ID"}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(course.toString()).doesNotContain("ChIJ_GOOGLE_ID");
    }

    @Test
    void 장소_조회가_예외를_던져도_코스_생성을_막지_않는다() throws Exception {
        PlaceRegistry failing = query -> {
            throw new IllegalStateException("provider down");
        };
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(failing).normalize(course, CONDITION);

        assertThat(stops(course)).hasSize(2);
        assertThat(stops(course).get(0).has("placeId")).isFalse();
    }

    @Test
    void itinerary_구조가_비어도_그대로_통과한다() throws Exception {
        JsonNode course = parse("{\"title\": \"코스\"}");

        new ItineraryPlaceNormalizer(new FakePlaceRegistry()).normalize(course, CONDITION);

        assertThat(course.path("title").asText()).isEqualTo("코스");
    }
}
