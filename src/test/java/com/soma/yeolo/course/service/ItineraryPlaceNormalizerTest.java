package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItineraryPlaceNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * AI가 장소 정보를 준 코스. 첫 장소에는 2026-10 개정으로 계약에서 빠진 상세(영문명·주소·평점·
     * 사진·운영시간)가 덤으로 섞여 있다 — 전환기 AI(DOM-5)가 아직 보낼 수 있다.
     */
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
                          "googlePlaceId": "ChIJ_SEONGSAN",
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
                          "googlePlaceId": "ChIJ_HYEOPJAE",
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

    /** 등록을 기록하는 fake 레지스트리. */
    private static final class FakePlaceRegistry implements PlaceRegistry {
        private final List<Place> registered = new ArrayList<>();

        @Override
        public SavedPlace register(Place place) {
            registered.add(place);
            return new SavedPlace(UUID.randomUUID(), place.placeName(), place.placeEngName(),
                    place.category(), place.address(), place.latitude(), place.longitude(),
                    place.rating(), place.photoUrl(), place.openingHours());
        }


        @Override
        public Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds) {
            throw new UnsupportedOperationException("정규화는 Google Place ID 역조회를 쓰지 않는다.");
        }
    }

    @Test
    void 각_장소에_내부_placeId와_좌표를_채운다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        JsonNode first = place(course, 0);
        assertThat(UUID.fromString(first.path("placeId").asText())).isNotNull();
        assertThat(first.path("latitude").asDouble()).isEqualTo(33.4581);
        assertThat(first.path("longitude").asDouble()).isEqualTo(126.9425);
        assertThat(place(course, 1).path("placeId").asText()).isNotBlank();
    }

    /** AI가 장소 식별자·좌표를 주므로(API-AI-2) 그대로 등록한다. */
    @Test
    void AI가_준_장소를_그대로_등록한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();

        new ItineraryPlaceNormalizer(registry).normalize(parse(AI_COURSE));

        assertThat(registry.registered).hasSize(2);
        Place first = registry.registered.get(0);
        assertThat(first.providerPlaceId()).isEqualTo("ChIJ_SEONGSAN");
        assertThat(first.placeName()).isEqualTo("성산일출봉");
        assertThat(first.category()).isEqualTo("nature");
        assertThat(first.latitude()).isEqualTo(33.4581);
        assertThat(first.longitude()).isEqualTo(126.9425);
    }

    /**
     * 2026-10 개정으로 AI 계약은 식별자·이름·분류·좌표뿐이다. 상세는 전환기 AI가 덤으로 주면 그대로
     * 담고(무손실, provider 폴백 경로와 같은 행 모양), 없으면 null이어야 한다 — 상세가 없다고
     * 폴백 조회를 타서는 안 된다.
     */
    @Test
    void 장소_상세는_주면_담고_없으면_null로_등록한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();

        new ItineraryPlaceNormalizer(registry).normalize(parse(AI_COURSE));

        Place withDetails = registry.registered.get(0);
        assertThat(withDetails.placeEngName()).isEqualTo("Seongsan Ilchulbong");
        assertThat(withDetails.rating()).isEqualTo(4.6);
        Place contractOnly = registry.registered.get(1);
        assertThat(contractOnly.providerPlaceId()).isEqualTo("ChIJ_HYEOPJAE");
        assertThat(contractOnly.placeEngName()).isNull();
        assertThat(contractOnly.address()).isNull();
        assertThat(contractOnly.rating()).isNull();
        assertThat(contractOnly.photoUrl()).isNull();
        assertThat(contractOnly.openingHours()).isEmpty();
    }

    /**
     * AI가 식별자·좌표를 주지 못한 stop 은 <b>외부 지도 API 를 부르지 않고</b> 정규화하지 않은 채 남긴다
     * (2026-11-04 결정 — 장소명 조회 폴백 제거). 이름·분류는 AI 값 그대로다.
     */
    @Test
    void 장소_정보가_부족하면_외부_조회_없이_정규화하지_않고_남긴다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {"placeName": "성산일출봉", "category": "nature"}},
                  {"sequence": 2, "place": {"placeName": "좌표만 있는 곳", "latitude": 33.5, "longitude": 126.5}},
                  {"sequence": 3, "place": {"googlePlaceId": "ChIJ_NO_COORDS", "placeName": "식별자만 있는 곳"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        assertThat(registry.registered).isEmpty();
        for (int i = 0; i < 3; i++) {
            assertThat(place(course, i).has("placeId")).as("stop %d", i + 1).isFalse();
            assertThat(place(course, i).has("googlePlaceId")).as("stop %d", i + 1).isFalse();
        }
        assertThat(place(course, 0).path("placeName").asText()).isEqualTo("성산일출봉");
        assertThat(place(course, 0).path("category").asText()).isEqualTo("nature");
        assertThat(place(course, 1).path("latitude").asDouble()).isEqualTo(33.5);
    }

    /** 숙소처럼 한 코스에서 반복 등장하는 장소를 매번 다시 등록하지 않는다. */
    @Test
    void 코스_안에서_같은_장소는_한_번만_등록한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [
                  {"day": 1, "stops": [{"sequence": 1, "place": {
                    "googlePlaceId": "ChIJ_HOTEL", "placeName": "제주 호텔",
                    "latitude": 33.5, "longitude": 126.5}}]},
                  {"day": 2, "stops": [{"sequence": 1, "place": {
                    "googlePlaceId": "ChIJ_HOTEL", "placeName": "제주 호텔",
                    "latitude": 33.5, "longitude": 126.5}}]}
                ]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        assertThat(registry.registered).hasSize(1);
        // 두 장소 모두 같은 내부 placeId로 채워진다.
        JsonNode days = course.path("itinerary").path("days");
        assertThat(days.get(1).path("stops").get(0).path("place").path("placeId").asText())
                .isEqualTo(days.get(0).path("stops").get(0).path("place").path("placeId").asText());
    }

    @Test
    void 정규화하지_못한_장소는_placeId_없이_남는다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {
                    "googlePlaceId": "ChIJ_SEONGSAN", "placeName": "성산일출봉",
                    "latitude": 33.4581, "longitude": 126.9425}},
                  {"sequence": 2, "place": {"placeName": "협재 해수욕장"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        JsonNode unresolved = place(course, 1);
        assertThat(unresolved.has("placeId")).isFalse();
        assertThat(unresolved.path("placeName").asText()).isEqualTo("협재 해수욕장");
        // 나머지 장소는 정상적으로 정규화된다.
        assertThat(place(course, 0).path("placeId").asText()).isNotBlank();
    }

    /** AI가 준 provider 식별자는 저장 JSON 에 남지 않는다 — 응답의 Google Place ID 는 장소 테이블에서 되돌린다. */
    @Test
    void AI가_보낸_googlePlaceId는_저장_JSON에_남지_않는다() throws Exception {
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(new FakePlaceRegistry()).normalize(course);

        assertThat(course.toString()).doesNotContain("ChIJ_SEONGSAN", "ChIJ_HYEOPJAE", "googlePlaceId");
    }

    /**
     * API-AI-2 개정(SPEC 0bd2d08, 2026-10-25) 전 키 {@code placeId}로만 식별자가 와도 같은 값으로 받는다.
     * 키 하나 때문에 검증된 식별자를 버리고 이름 조회(폴백)로 빠지면 안 된다 — prod 에서 그렇게 모든
     * 장소가 폴백을 타며 조용히 깨졌다(2026-11-04). 경고 로그는 남긴다.
     */
    @Test
    void 개정_전_키_placeId로만_온_식별자도_받는다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {
                    "placeId": "ChIJ_LEGACY", "placeName": "성산일출봉",
                    "latitude": 33.4581, "longitude": 126.9425}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        assertThat(registry.registered).extracting(Place::providerPlaceId).containsExactly("ChIJ_LEGACY");
        assertThat(UUID.fromString(place(course, 0).path("placeId").asText())).isNotNull();
        assertThat(course.toString()).doesNotContain("ChIJ_LEGACY");
    }

    /** 두 키가 같이 오면 명세의 {@code googlePlaceId}가 우선이고, 둘 다 저장 JSON 에서 지운다. */
    @Test
    void googlePlaceId와_placeId가_같이_오면_googlePlaceId를_쓴다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {
                    "googlePlaceId": "ChIJ_CURRENT", "placeId": "ChIJ_LEGACY", "placeName": "성산일출봉",
                    "latitude": 33.4581, "longitude": 126.9425}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        assertThat(registry.registered).extracting(Place::providerPlaceId).containsExactly("ChIJ_CURRENT");
        assertThat(place(course, 0).has("googlePlaceId")).isFalse();
        assertThat(UUID.fromString(place(course, 0).path("placeId").asText())).isNotNull();
        assertThat(course.toString()).doesNotContain("ChIJ_CURRENT", "ChIJ_LEGACY");
    }

    /** 정규화에 실패한 stop 에는 어느 키의 외부 식별자도 남지 않는다. */
    @Test
    void 정규화에_실패해도_googlePlaceId_키를_남기지_않는다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {"placeName": "성산일출봉", "googlePlaceId": "ChIJ_GOOGLE_ID"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        JsonNode unresolved = place(course, 0);
        assertThat(unresolved.has("googlePlaceId")).isFalse();
        assertThat(unresolved.has("placeId")).isFalse();
        assertThat(course.toString()).doesNotContain("ChIJ_GOOGLE_ID");
    }

    /** 정규화에 실패해도 AI가 준 외부 식별자를 — 개정 전 키로 왔더라도 — 그대로 남겨 두지 않는다. */
    @Test
    void 정규화에_실패해도_개정_전_키의_외부_식별자도_제거한다() throws Exception {
        FakePlaceRegistry registry = new FakePlaceRegistry();
        JsonNode course = parse("""
                {"itinerary": {"days": [{"day": 1, "stops": [
                  {"sequence": 1, "place": {"placeName": "성산일출봉", "placeId": "ChIJ_GOOGLE_ID"}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

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
            public Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds) {
                throw new UnsupportedOperationException("정규화는 Google Place ID 역조회를 쓰지 않는다.");
            }
        };
        JsonNode course = parse(AI_COURSE);

        new ItineraryPlaceNormalizer(failing).normalize(course);

        assertThat(places(course)).hasSize(2);
        assertThat(place(course, 0).has("placeId")).isFalse();
    }

    @Test
    void itinerary_구조가_비어도_그대로_통과한다() throws Exception {
        JsonNode course = parse("{\"title\": \"코스\"}");

        new ItineraryPlaceNormalizer(new FakePlaceRegistry()).normalize(course);

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
                    "googlePlaceId": "ChIJ_HYEOPJAE", "placeName": "협재 해수욕장",
                    "latitude": 33.3936, "longitude": 126.2396}}
                ]}]}}
                """);

        new ItineraryPlaceNormalizer(registry).normalize(course);

        assertThat(registry.registered).hasSize(1);
        assertThat(place(course, 1).path("placeId").asText()).isNotBlank();
    }
}
