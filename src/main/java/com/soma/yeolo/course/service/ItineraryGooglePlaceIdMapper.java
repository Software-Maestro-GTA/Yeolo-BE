package com.soma.yeolo.course.service;

import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.dto.Itinerary.Day;
import com.soma.yeolo.course.dto.Itinerary.Place;
import com.soma.yeolo.course.dto.Itinerary.Stop;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 코스 상세 응답의 stop {@code place.placeId}를 내부 UUID에서 <b>Google Place ID</b>로 바꾼다.
 * (API-COURSE-2, 2026-10-25 결정)
 *
 * <p>저장된 itinerary 에는 {@link ItineraryPlaceNormalizer}가 심은 내부 식별자가 들어 있다. FE는 장소를
 * 열 때 이 값을 Google Maps 에 Place ID 로 넘기므로, 응답 직전에 장소 테이블의 provider 식별자로
 * 되돌린다. 저장 JSON 은 건드리지 않는다 — 이미 저장된 코스도 조회 시점에 같은 규칙으로 바뀐다.
 *
 * <p>한 코스에서 같은 장소가 여러 stop 에 등장하는 일이 흔하므로(숙소 등) 식별자를 모아 한 번에
 * 조회한다. 되돌릴 수 없는 stop(식별자 없음, 장소 행 없음, Google 형식이 아닌 provider)은 {@code null}로
 * 내린다 — FE가 Google 에 넘겨도 찾을 수 없는 값을 주느니 비우는 편이 낫고, 응답 계약은 이전부터
 * {@code placeId} null 을 허용한다.
 *
 * <p><b>장소 조회 실패는 상세 조회를 실패시키지 않는다.</b> 코스 본문은 이미 읽었고 식별자는 부가
 * 정보이므로, 장소 테이블 조회가 예외를 내면 경고만 남기고 모든 {@code placeId}를 null 로 내린다
 * ({@code CourseQueryService.convertCost}의 환율 폴백과 같은 태도).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryGooglePlaceIdMapper {

    private final PlaceRegistry placeRegistry;

    public Itinerary toGooglePlaceIds(Itinerary itinerary) {
        if (itinerary == null || itinerary.days() == null) {
            return itinerary;
        }
        Set<UUID> internalIds = new LinkedHashSet<>();
        boolean anyPlaceId = false;
        for (Day day : itinerary.days()) {
            for (Stop stop : stopsOf(day)) {
                if (stop == null || stop.place() == null || stop.place().placeId() == null) {
                    continue;
                }
                anyPlaceId = true;
                UUID id = internalId(stop.place());
                if (id != null) {
                    internalIds.add(id);
                }
            }
        }
        if (!anyPlaceId) {
            // 모든 placeId 가 이미 null 이다 — 바꿀 것이 없으니 재조립하지 않는다.
            return itinerary;
        }
        // UUID 가 아닌 placeId 만 있었다면 조회할 것은 없지만, 그 값을 null 로 지우기 위해 재조립은 한다.
        Map<UUID, String> googleIds = internalIds.isEmpty() ? Map.of() : lookUp(internalIds);

        List<Day> days = new ArrayList<>(itinerary.days().size());
        for (Day day : itinerary.days()) {
            if (day == null || day.stops() == null) {
                days.add(day);
                continue;
            }
            List<Stop> stops = new ArrayList<>(day.stops().size());
            for (Stop stop : stopsOf(day)) {
                stops.add(withGooglePlaceId(stop, googleIds));
            }
            days.add(new Day(day.day(), day.date(), stops));
        }
        return new Itinerary(days);
    }

    private Map<UUID, String> lookUp(Set<UUID> internalIds) {
        try {
            return placeRegistry.findGooglePlaceIds(internalIds);
        } catch (RuntimeException e) {
            log.warn("장소 식별자 조회에 실패해 placeId 없이 응답한다. count={}", internalIds.size(), e);
            return Map.of();
        }
    }

    private static List<Stop> stopsOf(Day day) {
        return day == null || day.stops() == null ? List.of() : day.stops();
    }

    private static Stop withGooglePlaceId(Stop stop, Map<UUID, String> googleIds) {
        if (stop == null || stop.place() == null) {
            return stop;
        }
        Place place = stop.place();
        UUID id = internalId(place);
        String googlePlaceId = id == null ? null : googleIds.get(id);
        Place mapped = new Place(googlePlaceId, place.placeName(), place.category(),
                place.latitude(), place.longitude());
        return new Stop(stop.sequence(), stop.stayMinutes(), stop.memo(), stop.reason(), stop.cost(),
                mapped, stop.transportToNext());
    }

    /** 읽은 placeId 는 {@code LenientUuidDeserializer}가 UUID 정규형만 통과시키지만, 방어적으로 되읽는다. */
    private static UUID internalId(Place place) {
        if (place == null || place.placeId() == null) {
            return null;
        }
        try {
            return UUID.fromString(place.placeId());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
