package com.soma.yeolo.tasteprofile.client;

import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reverse Geocode 스텁 구현(기본값). 좌표를 받아 고정된 위치 정보를 반환한다.
 *
 * <p>{@code geocode.provider=google}로 설정하면 {@link GoogleReverseGeocodeClient}가 대신 활성화된다.
 * 스텁은 설정이 없거나 {@code geocode.provider=stub}일 때 사용되며, provider 없이도
 * 파이프라인·SSE 중계·저장 흐름을 좌표 값과 무관하게 검증할 수 있게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "geocode.provider", havingValue = "stub", matchIfMissing = true)
public class StubReverseGeocodeClient implements ReverseGeocodeClient {

    @Override
    public GeoLocation reverseGeocode(double latitude, double longitude) {
        log.debug("Reverse geocode (stub) for ({}, {})", latitude, longitude);
        return new GeoLocation(
                "대한민국",
                "미상",
                "미상",
                "미상",
                "미상",
                List.of("point_of_interest")
        );
    }
}
