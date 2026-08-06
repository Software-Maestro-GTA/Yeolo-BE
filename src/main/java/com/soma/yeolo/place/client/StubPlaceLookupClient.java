package com.soma.yeolo.place.client;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 장소 조회 스텁 구현(기본값). 외부 호출 없이 장소명에서 결정론적으로 합성한 결과를 돌려준다.
 *
 * <p>{@code place.provider=google|osm}으로 설정하면 실 provider 구현이 대신 활성화된다. 스텁은
 * API 키·외부 네트워크 없이도 "코스 생성 → 장소 정규화 → 내부 placeId 부여 → 장소 상세 조회"
 * 전체 흐름을 검증할 수 있게 한다.
 *
 * <p>같은 장소명은 항상 같은 {@code providerPlaceId}·좌표로 매핑되므로, 코스를 여러 번 생성해도
 * 장소가 중복 저장되지 않는 실 provider의 동작을 그대로 흉내낸다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "place.provider", havingValue = "stub", matchIfMissing = true)
public class StubPlaceLookupClient implements PlaceLookupClient {

    /** 합성 좌표 범위 — 대한민국 본토를 대략 감싸는 사각형(지도에 찍었을 때 어색하지 않을 정도). */
    private static final double MIN_LATITUDE = 33.2;
    private static final double LATITUDE_SPAN = 5.2;
    private static final double MIN_LONGITUDE = 126.2;
    private static final double LONGITUDE_SPAN = 3.3;
    /** 좌표 소수 4자리(약 11m) 단위로 떨어뜨린다. */
    private static final double COORD_SCALE = 10_000.0;

    @Override
    public Optional<Place> lookup(PlaceQuery query) {
        log.debug("Place lookup (stub) for '{}'", query.searchText());
        // String.hashCode()는 JLS에 규정되어 있어 JVM·플랫폼과 무관하게 같은 값이 나온다.
        long hash = query.placeName().hashCode() & 0x7fffffffL;
        return Optional.of(new Place(
                "stub:" + Long.toHexString(hash),
                query.placeName(),
                query.category() != null ? query.category() : "point_of_interest",
                address(query),
                coordinate(hash, MIN_LATITUDE, LATITUDE_SPAN),
                coordinate(hash >> 11, MIN_LONGITUDE, LONGITUDE_SPAN),
                4.5,
                List.of(),
                List.of("월~일 09:00~18:00")
        ));
    }

    private String address(PlaceQuery query) {
        return Stream.of(query.country(), query.city(), query.placeName())
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(" "));
    }

    /** 해시를 [min, min+span) 구간의 좌표로 사상한다. */
    private double coordinate(long hash, double min, double span) {
        double fraction = (hash & 0xfffffL) / (double) 0x100000L;
        return Math.round((min + fraction * span) * COORD_SCALE) / COORD_SCALE;
    }
}
