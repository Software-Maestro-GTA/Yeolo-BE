package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.dto.Itinerary.Day;
import com.soma.yeolo.course.dto.Itinerary.Place;
import com.soma.yeolo.course.dto.Itinerary.Stop;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 저장 JSON 의 빈 구조·null 구멍을 그대로 통과시키는지, 바꿀 것이 없으면 재조립하지 않는지. */
class ItineraryGooglePlaceIdMapperTest {

    private static final String GOOGLE_PLACE_ID = "ChIJN1t_tDeuEmsRUsoyG83frY4";

    private static final class FakePlaceRegistry implements PlaceRegistry {
        private final Map<UUID, String> googleIds = new HashMap<>();
        private final List<Collection<UUID>> lookups = new ArrayList<>();

        @Override
        public SavedPlace register(com.soma.yeolo.place.domain.Place place) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SavedPlace> resolve(PlaceQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, String> findGooglePlaceIds(Collection<UUID> placeIds) {
            lookups.add(List.copyOf(placeIds));
            Map<UUID, String> result = new HashMap<>();
            placeIds.forEach(id -> {
                if (googleIds.containsKey(id)) {
                    result.put(id, googleIds.get(id));
                }
            });
            return result;
        }
    }

    private final FakePlaceRegistry registry = new FakePlaceRegistry();
    private final ItineraryGooglePlaceIdMapper mapper = new ItineraryGooglePlaceIdMapper(registry);

    private static Stop stop(Place place) {
        return new Stop(1, null, null, null, null, place, null);
    }

    @Test
    void 바꿀_placeId가_없으면_장소를_조회하지_않고_같은_객체를_돌려준다() {
        Itinerary itinerary = new Itinerary(List.of(
                new Day(1, "2026-08-01", List.of(stop(new Place(null, "이름만", null, null, null)), stop(null)))));

        assertThat(mapper.toGooglePlaceIds(itinerary)).isSameAs(itinerary);
        assertThat(registry.lookups).isEmpty();
    }

    @Test
    void days나_stops가_null인_저장_JSON도_그대로_통과한다() {
        UUID placeId = UUID.randomUUID();
        registry.googleIds.put(placeId, GOOGLE_PLACE_ID);
        List<Day> days = Arrays.asList(
                null,
                new Day(2, "2026-08-02", null),
                new Day(3, "2026-08-03", Arrays.asList(null, stop(null),
                        stop(new Place(placeId.toString(), "성산일출봉", null, 33.4581, 126.9425)))));

        Itinerary mapped = mapper.toGooglePlaceIds(new Itinerary(days));

        assertThat(mapped.days()).hasSize(3);
        assertThat(mapped.days().get(0)).isNull();
        assertThat(mapped.days().get(1).stops()).isNull();
        List<Stop> stops = mapped.days().get(2).stops();
        assertThat(stops.get(0)).isNull();
        assertThat(stops.get(1).place()).isNull();
        assertThat(stops.get(2).place().placeId()).isEqualTo(GOOGLE_PLACE_ID);
        assertThat(stops.get(2).place().latitude()).isEqualTo(33.4581);
        assertThat(mapper.toGooglePlaceIds(null)).isNull();
        assertThat(mapper.toGooglePlaceIds(new Itinerary(null)).days()).isNull();
    }

    @Test
    void UUID가_아닌_placeId는_조회하지_않고_null로_내린다() {
        Itinerary mapped = mapper.toGooglePlaceIds(new Itinerary(List.of(
                new Day(1, null, List.of(stop(new Place("ChIJ_RAW_FROM_AI", "장소", null, null, null)))))));

        assertThat(mapped.days().get(0).stops().get(0).place().placeId()).isNull();
        assertThat(registry.lookups).isEmpty();
    }
}
