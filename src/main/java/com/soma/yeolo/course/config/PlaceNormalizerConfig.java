package com.soma.yeolo.course.config;

import com.soma.yeolo.course.client.OsmPlaceNormalizer;
import com.soma.yeolo.course.client.PlaceNormalizer;
import com.soma.yeolo.course.client.StubPlaceNormalizer;
import com.soma.yeolo.tasteprofile.client.OsmGeocodeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 장소 정규화({@link PlaceNormalizer}) provider 선택 구성. {@code geocode.provider=osm}이면 Nominatim
 * Forward Geocode 구현을, 그 외(설정 없음·{@code stub}·{@code google})는 스텁을 기본 빈으로 등록한다.
 *
 * <p>{@code @ConditionalOnMissingBean} 폴백을 써서 어떤 provider 값에서도 {@link PlaceNormalizer}
 * 빈이 반드시 하나 존재하도록 보장한다(누락 시 기동 실패 방지).
 */
@Configuration
public class PlaceNormalizerConfig {

    @Bean
    @ConditionalOnProperty(name = "geocode.provider", havingValue = "osm")
    public PlaceNormalizer osmPlaceNormalizer(@Qualifier("restClient") RestClient restClient,
                                              OsmGeocodeProperties properties) {
        return new OsmPlaceNormalizer(restClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlaceNormalizer.class)
    public PlaceNormalizer stubPlaceNormalizer() {
        return new StubPlaceNormalizer();
    }
}
