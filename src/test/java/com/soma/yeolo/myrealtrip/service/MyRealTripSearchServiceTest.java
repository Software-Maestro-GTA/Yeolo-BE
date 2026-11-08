package com.soma.yeolo.myrealtrip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.FlightSearch;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import com.soma.yeolo.myrealtrip.dto.AirportSearchResponse;
import com.soma.yeolo.myrealtrip.dto.RegionSearchResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 검색 서비스 — 입력 검증(400)·한도(429)·파트너 호출 순서와 응답 매핑을 fake 포트로 검증한다. */
class MyRealTripSearchServiceTest {

    private final FakePartnerClient partner = new FakePartnerClient();
    private final MyRealTripRateLimiter limiter = new MyRealTripRateLimiter(2, Duration.ofMinutes(1), Clock.systemUTC());
    private final MyRealTripSearchService service = new MyRealTripSearchService(partner, limiter);
    private final UUID userId = UUID.randomUUID();

    @Test
    void 공항_검색은_키워드_앞뒤_공백을_지워_파트너에_넘기고_airport_중첩_구조로_응답한다() {
        partner.airports = List.of(new Airport("ICN", "인천국제공항", "Incheon International Airport"),
                new Airport("GMP", null, null));

        AirportSearchResponse response = service.searchAirports(userId, "  인천 ");

        assertThat(partner.airportKeywords).containsExactly("인천");
        assertThat(response.airports()).hasSize(2);
        assertThat(response.airports().get(0).airport().code()).isEqualTo("ICN");
        assertThat(response.airports().get(0).airport().koName()).isEqualTo("인천국제공항");
        assertThat(response.airports().get(1).airport().code()).isEqualTo("GMP");
        assertThat(response.airports().get(1).airport().koName()).isNull();
    }

    @Test
    void 키워드가_없거나_공백뿐이면_400이고_파트너를_부르지_않으며_한도도_소모하지_않는다() {
        assertInvalid(() -> service.searchAirports(userId, null));
        assertInvalid(() -> service.searchAirports(userId, "   "));
        assertInvalid(() -> service.searchRegions(userId, "", "true"));
        assertThat(partner.airportKeywords).isEmpty();
        assertThat(partner.regionCalls).isEmpty();

        // 한도 2 — 잘못된 요청 셋이 한도를 먹었다면 아래 둘 중 하나가 429 였을 것이다.
        service.searchAirports(userId, "인천");
        service.searchAirports(userId, "김포");
    }

    @Test
    void 숙박_지역_검색의_isDomestic은_true_false만_받고_누락이나_다른_값은_400이다() {
        partner.regions = List.of(new Region(12345, "도쿄", "일본"));

        RegionSearchResponse response = service.searchRegions(userId, "도쿄", "false");
        assertThat(partner.regionCalls).containsExactly("도쿄|false");
        assertThat(response.regions()).containsExactly(new RegionSearchResponse.Entry(12345, "도쿄", "일본"));

        service.searchRegions(userId, "서울", "TRUE");
        assertThat(partner.regionCalls).containsExactly("도쿄|false", "서울|true");

        assertInvalid(() -> service.searchRegions(userId, "도쿄", null));
        assertInvalid(() -> service.searchRegions(userId, "도쿄", "1"));
        assertInvalid(() -> service.searchRegions(userId, "도쿄", "yes"));
        assertThat(partner.regionCalls).hasSize(2);
    }

    @Test
    void 사용자_한도를_넘으면_429이고_파트너를_부르지_않는다() {
        service.searchAirports(userId, "인천");
        service.searchRegions(userId, "도쿄", "false");   // 공항·지역이 한 한도를 공유한다

        assertThatThrownBy(() -> service.searchAirports(userId, "김포"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(partner.airportKeywords).containsExactly("인천");
        assertThat(partner.regionCalls).containsExactly("도쿄|false");
    }

    @Test
    void 인증_주체가_없으면_401이고_파트너를_부르지_않는다() {
        assertThatThrownBy(() -> service.searchAirports(null, "인천"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(partner.airportKeywords).isEmpty();
    }

    @Test
    void 결과가_없으면_빈_목록이다() {
        assertThat(service.searchAirports(userId, "없는곳").airports()).isEmpty();
        assertThat(service.searchRegions(userId, "없는곳", "true").regions()).isEmpty();
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MYREALTRIP_REQUEST);
    }

    private static final class FakePartnerClient implements MyRealTripPartnerClient {
        List<Airport> airports = List.of();
        List<Region> regions = List.of();
        final List<String> airportKeywords = new ArrayList<>();
        final List<String> regionCalls = new ArrayList<>();

        @Override
        public List<Airport> searchAirports(String keyword) {
            airportKeywords.add(keyword);
            return airports;
        }

        @Override
        public List<Region> searchRegions(String keyword, boolean isDomestic) {
            regionCalls.add(keyword + "|" + isDomestic);
            return regions;
        }

        @Override
        public ProductPage searchHotels(HotelSearchQuery query) {
            throw new UnsupportedOperationException("상품 조회는 MyRealTripProductServiceTest 가 검증한다");
        }

        @Override
        public ProductPage searchTickets(String keyword, int page) {
            throw new UnsupportedOperationException("상품 조회는 MyRealTripProductServiceTest 가 검증한다");
        }

        @Override
        public String createFlightLink(FlightSearch search) {
            throw new UnsupportedOperationException("링크 생성은 MyRealTripLinkServiceTest 가 검증한다");
        }

        @Override
        public String createProductLink(String targetUrl) {
            throw new UnsupportedOperationException("링크 생성은 MyRealTripLinkServiceTest 가 검증한다");
        }
    }
}
