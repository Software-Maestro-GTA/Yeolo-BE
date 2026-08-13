package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.BudgetType;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.place.domain.Place;
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
            "대한민국", "제주", LocalDate.of(2026, 8, 1), 2, BudgetType.MODERATE);

    /** AI가 장소 정보를 온전히 준 코스 (API-AI-2 개정판). */
    private static final String AI_COURSE = """
            {
              "title": "2박 3일 제주 힐링 코스",
              "itinerary": {
                "days": [
                  {
                    "day": 1,
                    "stops": [
                      {
                        "sequence": 1,
                        "place": {
                          "placeId": "ChIJ_SEONGSAN",
                          "placeName": "성산일출봉",
                          "placeEngName": "Seongsan Ilchulbong",
                          "category": "nature",
                          "address": "제주특별자치도 서귀포시 성산읍",
                          "latitude": 33.4581,
                          "longitude": 126.9425,
                          "rating": 4.6,
                          "photoUrl": "https://cdn.example.com/seongsan.jpg",
                          "openingHours": ["매일 07:00~20:00"]
                        },
                        "transportToNext": {"type": "driving", "minutes": 40}
                      },
                      {
                        "sequence": 2,
                        "place": {
                          "placeId": "ChIJ_HYEOPJAE",
                          "placeName": "협재 해수욕장",
                          "category": "beach",
                          "latitude": 33.3936,
                          "longitude": 126.2396
                        }
                      }
                    ]
                  }
                ]
              }
            }
            """;

    private JsonNode parse(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private JsonNode places(JsonNode course) {
        return course.path("itinerary").path("days").get(0).path("stops");
    }

    private JsonNode place(JsonNode course, int index) {
        return places(course).get(index).path("place");
    }

    /**
     * 등록·조회를 모두 기록하는 fake 레지스트리. 조회(폴백)는 장소명을 그대로 좌표에 매핑한다.
     */
    private static final class FakePlaceRegistry implements PlaceRegistry {
        private final List<Place> registered = new ArrayList<>();
        private final List<PlaceQuery> queries = new ArrayList<>();
        private final List<String> unresolvable;

        private FakePlaceRegistry(String... unresolvable) {
            this.unresolvable = List.of(unresolvable);
        }

        @Override
        public SavedPlace register(Place place) {
            registered.add(place);
            return new SavedPlace(UUID.randomUUID(), place.placeName(), place.placeEngName(),
                    place.category(), place.address(), place.latitude(), place.longitude(),
                    place.rating(), place.photoUrl(), place.openingHours());
        }

        @Override
        public Optional<SavedPlace> resolve(PlaceQuery query) {
            queries.add(query);
            if (unresolvable.contains(query.placeName())) {
                return Optional.empty();
            }
            return Optional.of(new SavedPlace(UUID.randomUUID(), query.placeName(), null,
                    query.category(), "제주", 33.4581, 126.9425, null, null, List.of()));
        }
    }

    @Test
    void 각_장소에_내부_placeId와_좌표를_채운다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        JsonNode first = place(course, 0);
        assertThat(UUID.fromString(first.path("placeId").asText())).isNotNull();
        assertThat(first.path("latitude").asDouble()).isEqualTo(33.4581);
        assertThat(first.path("longitude").asDouble()).isEqualTo(126.9425);
        assertThat(place(course, 1).path("placeId").asText()).isNotBlank();
    }

    /** AI가 장소를 통째로 주므로(API-AI-2 개정) 외부 provider를 다시 부르지 않는다. */
    @Test
    void AI가_준_장소는_provider_조회_없이_그대로_등록한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();

        new ItineraryPlaceNormalizer(registry).normalize(parse(AI_COURSE), CONDITION);

        assertThat(registry.queries).isEmpty();
        assertThat(registry.registered).hasSize(2);
        Place first = registry.registered.get(0);
        assertThat(first.providerPlaceId()).isEqualTo("ChIJ_SEONGSAN");
        assertThat(first.placeEngName()).isEqualTo("Seongsan Ilchulbong");
        assertThat(first.address()).isEqualTo("제주특별자치도 서귀포시 성산읍");
        assertThat(first.rating()).isEqualTo(4.6);
        assertThat(first.photoUrl()).isEqualTo("https://cdn.example.com/seongsan.jpg");
        assertThat(first.openingHours()).containsExactly("매일 07:00~20:00");
    }

    /** AI가 식별자·좌표를 주지 못하면 예전 경로(장소명 조회)로 되돌아간다. */
    @Test
    void 장소_정보가_부족하면_장소명으로_조회한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {"placeName": "성산일출봉", "category": "nature"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(registry.registered).isEmpty();
        assertThat(registry.queries).hasSize(1);
        // 목적지를 붙여 동명 장소를 구분한다.
        assertThat(registry.queries.get(0).searchText()).isEqualTo("성산일출봉, 제주, 대한민국");
        assertThat(registry.queries.get(0).category()).isEqualTo("nature");
        assertThat(place(course, 0).path("placeId").asText()).isNotBlank();
    }

    /** 숙소처럼 한 코스에서 반복 등장하는 장소를 매번 다시 등록하지 않는다. */
    @Test
    void 코스_안에서_같은_장소는_한_번만_등록한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [
                  {"day": 1, "stops": [{"sequence": 1, "place": {
                    "placeId": "ChIJ_HOTEL", "placeName": "제주 호텔",
                    "latitude": 33.5, "longitude": 126.5}}]},
                  {"day": 2, "stops": [{"sequence": 1, "place": {
                    "placeId": "ChIJ_HOTEL", "placeName": "제주 호텔",
                    "latitude": 33.5, "longitude": 126.5}}]}
                ]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(registry.registered).hasSize(1);
        // 두 장소 모두 같은 내부 placeId로 채워진다.
        JsonNode days = course.path("itinerary").path("days");
        assertThat(days.get(1).path("stops").get(0).path("place").path("placeId").asText())
                .isEqualTo(days.get(0).path("stops").get(0).path("place").path("placeId").asText());
    }

    @Test
    void 정규화하지_못한_장소는_placeId_없이_남는다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry("협재 해수욕장");
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {
                    "placeId": "ChIJ_SEONGSAN", "placeName": "성산일출봉",
                    "latitude": 33.4581, "longitude": 126.9425}},
                  {"sequence": 2, "place": {"placeName": "협재 해수욕장"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        JsonNode unresolved = place(course, 1);
        assertThat(unresolved.has("placeId")).isFalse();
        assertThat(unresolved.path("placeName").asText()).isEqualTo("협재 해수욕장");
        // 나머지 장소는 정상적으로 정규화된다.
        assertThat(place(course, 0).path("placeId").asText()).isNotBlank();
    }

    /** AI가 준 provider 식별자는 FE 응답으로 새어 나가면 안 된다 (DOM-3). */
    @Test
    void AI가_보낸_provider_placeId는_응답에_남지_않는다() throws Exception {
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(new FakePlaceRegistry()).normalize(course, CONDITION);

        assertThat(course.toString()).doesNotContain("ChIJ_SEONGSAN", "ChIJ_HYEOPJAE");
    }

    /** 정규화에 실패해도 AI가 준 외부 식별자를 그대로 남겨 두지 않는다. */
    @Test
    void 정규화에_실패해도_외부_식별자는_제거한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry("성산일출봉");
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {"placeName": "성산일출봉", "placeId": "ChIJ_GOOGLE_ID"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(course.toString()).doesNotContain("ChIJ_GOOGLE_ID");
    }

    @Test
    void 장소_등록이_예외를_던져도_코스_생성을_막지_않는다() throws Exception {
        PlaceRegistry failing = new PlaceRegistry() {
            @Override
            public SavedPlace register(Place place) {
                throw new IllegalStateException("db down");
            }

            @Override
            public Optional<SavedPlace> resolve(PlaceQuery query) {
                throw new IllegalStateException("provider down");
            }
        };
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(failing).normalize(course, CONDITION);

        assertThat(places(course)).hasSize(2);
        assertThat(place(course, 0).has("placeId")).isFalse();
    }

    @Test
    void itinerary_구조가_비어도_그대로_통과한다() throws Exception {
        JsonNode course = parse("{\"title\": \"코스\"}");

        new ItineraryPlaceNormalizer(new FakePlaceRegistry()).normalize(course, CONDITION);

        assertThat(course.path("title").asText()).isEqualTo("코스");
    }

    /** place 객체가 아예 없는 stop(옛 형식)이 섞여도 나머지 정규화는 계속된다. */
    @Test
    void place가_없는_stop은_건너뛴다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "placeName": "옛 형식 stop"},
                  {"sequence": 2, "place": {
                    "placeId": "ChIJ_HYEOPJAE", "placeName": "협재 해수욕장",
                    "latitude": 33.3936, "longitude": 126.2396}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course, CONDITION);

        assertThat(registry.registered).hasSize(1);
        assertThat(place(course, 1).path("placeId").asText()).isNotBlank();
    }
}
