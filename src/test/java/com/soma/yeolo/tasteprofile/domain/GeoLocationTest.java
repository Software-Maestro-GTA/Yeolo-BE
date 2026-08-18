package com.soma.yeolo.tasteprofile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoLocationTest {

    private GeoLocation location(String country, String city, String region, String district, String placeName) {
        return new GeoLocation(country, city, region, district, placeName, List.of("cafe"));
    }

    @Test
    void 필수_필드가_모두_채워지면_분석_가능하다() {
        GeoLocation location = location("대한민국", "마포구", "서울특별시", "공덕동", "프릳츠 도화");

        assertThat(location.isAnalyzable()).isTrue();
    }

    /**
     * AI의 {@code LocationSchema}(API-AI-1)는 placeTypes만 optional이고 나머지는 non-nullable이다.
     * 하나라도 비면 요청 전체가 400("분석 가능한 전처리 메타데이터가 부족합니다.")으로 반려된다.
     */
    @ParameterizedTest(name = "{5}이 비면 분석 불가")
    @CsvSource(nullValues = "null", value = {
            "null,      마포구, 서울특별시, 공덕동, 프릳츠 도화, country",
            "대한민국, null,   서울특별시, 공덕동, 프릳츠 도화, city",
            "대한민국, 마포구, null,       공덕동, 프릳츠 도화, region",
            "대한민국, 마포구, 서울특별시, null,   프릳츠 도화, district",
            "대한민국, 마포구, 서울특별시, 공덕동, null,        placeName",
    })
    void 필수_필드가_하나라도_비면_분석_불가다(String country, String city, String region,
                                            String district, String placeName, String missing) {
        assertThat(location(country, city, region, district, placeName).isAnalyzable())
                .as("%s 누락", missing)
                .isFalse();
    }

    @Test
    void 공백만_있는_값은_비어_있는_것으로_본다() {
        GeoLocation location = location("대한민국", "   ", "서울특별시", "공덕동", "프릳츠 도화");

        assertThat(location.isAnalyzable()).isFalse();
    }

    @Test
    void placeTypes는_비어도_분석_가능하다() {
        GeoLocation location = new GeoLocation("대한민국", "마포구", "서울특별시", "공덕동", "프릳츠 도화", null);

        assertThat(location.placeTypes()).isEmpty();
        assertThat(location.isAnalyzable()).isTrue();
    }
}
