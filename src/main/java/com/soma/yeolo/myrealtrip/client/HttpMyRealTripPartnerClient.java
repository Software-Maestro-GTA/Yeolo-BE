package com.soma.yeolo.myrealtrip.client;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.global.client.RetryAfterHeader;
import com.soma.yeolo.myrealtrip.client.dto.PartnerAirportsData;
import com.soma.yeolo.myrealtrip.client.dto.PartnerEnvelope;
import com.soma.yeolo.myrealtrip.client.dto.PartnerMylinkData;
import com.soma.yeolo.myrealtrip.client.dto.PartnerProductsData;
import com.soma.yeolo.myrealtrip.client.dto.PartnerRegionsData;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.FlightSearch;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.MyRealTripUrls;
import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 마이리얼트립 파트너 API HTTP 어댑터. 경로·요청 형식·성공 판정은 이관 전 FE 어댑터(Yeolo-FE
 * {@code packages/common/src/api/myrealtrip.ts})를 근거로 한다 — 명세(API-MRT-*)는 파트너 경로만 적고 본문
 * 형식은 "현재 FE 어댑터"를 가리킨다.
 *
 * <p><b>실패 매핑(명세 §실패 응답).</b> 파트너의 401·403 은 BE 의 키·등록 IP 문제이므로 502 로 바꾼다 — 그대로
 * 401 을 내면 FE 가 로그인 만료로 처리해 사용자를 재로그인시킨다. 파트너 429 는 429 로 전달하고
 * {@code Retry-After}가 숫자면 그대로 싣는다. 그 밖의 HTTP 오류·잘못된 응답 구조는 502, 응답 시간 초과는 504 다.
 * 키·파트너 원문 오류는 응답에 싣지 않고 로그에만 남긴다.
 */
@Slf4j
@Component
public class HttpMyRealTripPartnerClient implements MyRealTripPartnerClient {

    static final String AIRPORT_AUTOCOMPLETE_PATH = "/v1/products/flight/airport-autocomplete";
    static final String REGION_AUTOCOMPLETE_PATH = "/v1/products/accommodation/region-autocomplete";
    static final String HOTEL_SEARCH_PATH = "/v1/products/accommodation/search";
    static final String TICKET_SEARCH_PATH = "/v1/products/tna/search";
    static final String FLIGHT_LANDING_URL_PATH = "/v1/products/flight/fare-query-landing-url";
    static final String MYLINK_PATH = "/v1/mylink";
    /** 왕복 고정 (API-MRT-5 §처리 규칙). 편도·다구간은 별도 요구사항. */
    static final String ROUND_TRIP = "RT";
    /** 공항 자동완성 결과 수. Client 에서 받지 않고 서버가 고정한다(API-MRT-1 §처리 규칙). */
    static final int AIRPORT_RESULT_SIZE = 10;
    /** 파트너 오류 본문은 로그에만 남기되 길이를 자른다 — 장애 중 자동완성이 입력마다 HTML 페이지를 통째로 찍지 않게. */
    private static final int ERROR_BODY_LOG_LIMIT = 500;

    private static final ParameterizedTypeReference<PartnerEnvelope<PartnerAirportsData>> AIRPORTS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PartnerEnvelope<PartnerRegionsData>> REGIONS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PartnerEnvelope<PartnerProductsData>> PRODUCTS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    /** 랜딩 URL API 의 {@code data}는 객체가 아니라 URL 문자열 하나다(이관 전 FE 어댑터 {@code post<string>}). */
    private static final ParameterizedTypeReference<PartnerEnvelope<String>> LANDING_URL_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PartnerEnvelope<PartnerMylinkData>> MYLINK_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final MyRealTripProperties properties;
    /** 설정값 끝의 슬래시를 지운 호스트. 경로 상수가 {@code /v1/...}로 시작하므로 {@code //} 를 만들지 않는다. */
    private final String baseUrl;

    public HttpMyRealTripPartnerClient(@Qualifier("myRealTripRestClient") RestClient restClient,
                                       MyRealTripProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().strip().replaceAll("/+$", "");
    }

    @Override
    public List<Airport> searchAirports(String keyword) {
        Objects.requireNonNull(keyword, "keyword");
        PartnerAirportsData data = post(AIRPORT_AUTOCOMPLETE_PATH,
                Map.of("keyword", keyword, "size", AIRPORT_RESULT_SIZE), AIRPORTS_TYPE);
        if (data.airports() == null) {
            throw invalidResponse(AIRPORT_AUTOCOMPLETE_PATH, "airports 배열 없음");
        }
        return data.airports().stream()
                .filter(Objects::nonNull)
                .map(PartnerAirportsData.Entry::airport)
                .filter(info -> info != null && Airport.isValidCode(info.code()))
                .map(info -> new Airport(info.code(), info.koName(), info.enName()))
                .toList();
    }

    @Override
    public List<Region> searchRegions(String keyword, boolean isDomestic) {
        Objects.requireNonNull(keyword, "keyword");
        PartnerRegionsData data = post(REGION_AUTOCOMPLETE_PATH,
                Map.of("keyword", keyword, "isDomestic", isDomestic), REGIONS_TYPE);
        if (data.regions() == null) {
            throw invalidResponse(REGION_AUTOCOMPLETE_PATH, "regions 배열 없음");
        }
        return data.regions().stream()
                .filter(entry -> entry != null && entry.regionId() != null && entry.name() != null)
                .map(entry -> new Region(entry.regionId(), entry.name(), entry.subName()))
                .toList();
    }

    /**
     * 숙소 상위 API 의 페이지는 0 부터 시작한다(이관 전 FE 어댑터 기준, 명세 §처리 규칙 "upstreamPage = page - 1").
     * FE→BE 는 1 부터이므로 여기서 변환한다. 응답의 {@code page}는 Client 가 보낸 값을 그대로 돌려준다.
     */
    @Override
    public ProductPage searchHotels(HotelSearchQuery query) {
        Objects.requireNonNull(query, "query");
        PartnerProductsData data = post(HOTEL_SEARCH_PATH, Map.of(
                "regionId", query.regionId(),
                "checkIn", query.checkIn().toString(),
                "checkOut", query.checkOut().toString(),
                "adultCount", query.adultCount(),
                "page", query.page() - 1,
                "size", ProductPage.PAGE_SIZE), PRODUCTS_TYPE);
        return toProductPage(HOTEL_SEARCH_PATH, data, query.page());
    }

    /** 투어·티켓 상위 API 의 페이지는 FE→BE 와 같이 1 부터다. */
    @Override
    public ProductPage searchTickets(String keyword, int page) {
        Objects.requireNonNull(keyword, "keyword");
        PartnerProductsData data = post(TICKET_SEARCH_PATH,
                Map.of("keyword", keyword, "page", page, "size", ProductPage.PAGE_SIZE), PRODUCTS_TYPE);
        return toProductPage(TICKET_SEARCH_PATH, data, page);
    }

    /**
     * 상품 목록 공통 변환(명세 §상품 및 페이지 필드). 상품명·검증된 원본 URL·0 이상 유한한 가격이 없는 항목은 제외하되
     * {@code hasMore}는 필터링 전 원본 수로 판단한다. {@code items} 자체가 없으면 빈 결과로 숨기지 않고 502 다.
     */
    private ProductPage toProductPage(String path, PartnerProductsData data, int page) {
        if (data.items() == null) {
            throw invalidResponse(path, "items 배열 없음");
        }
        List<Product> products = data.items().stream()
                .map(HttpMyRealTripPartnerClient::parseProduct)
                .flatMap(Optional::stream)
                .toList();
        int dropped = data.items().size() - products.size();
        if (dropped > 0) {
            // 제외는 정상 동작이지만 "20개 중 3개만 보인다"를 진단할 단서는 남긴다. 항목 원문은 찍지 않는다.
            log.warn("마이리얼트립 {} page={} 상품 {}건 중 {}건 제외(상품명·HTTPS myrealtrip.com URL·0 이상 가격 요건 미달)",
                    path, page, data.items().size(), dropped);
        }
        return ProductPage.of(products, page, upstreamTotalCount(data.totalCount()), data.items().size());
    }

    /**
     * 유효한 상품이면 값, 아니면 빈 값(제외). JSON 객체가 아닌 항목도 제외다. Client 가 쓰지 않는
     * category·tags·priceDisplay 는 읽지 않는다.
     */
    private static Optional<Product> parseProduct(Object item) {
        if (!(item instanceof Map<?, ?> node)) {
            return Optional.empty();
        }
        String itemName = text(node, "itemName");
        String productUrl = text(node, "productUrl");
        BigDecimal salePrice = number(node, "salePrice");
        if (itemName == null || !MyRealTripUrls.isProductUrl(productUrl)
                || salePrice == null || salePrice.signum() < 0) {
            return Optional.empty();
        }
        Object reviewScore = node.get("reviewScore");
        Object score = reviewScore instanceof String || reviewScore instanceof Number ? reviewScore : null;
        Integer count = integer(node, "reviewCount");
        return Optional.of(new Product(itemName, productUrl, salePrice, text(node, "imageUrl"),
                text(node, "description"), number(node, "starRating"), score, count));
    }

    /** 상위 API 가 준 전체 건수. 0 이상 정수(정수값 실수 {@code 45.0} 포함)만 믿고 그 밖은 "미제공"으로 본다. */
    private static Long upstreamTotalCount(Object value) {
        Long count = integral(value);
        return count != null && count >= 0 ? count : null;
    }

    /** 정수로 읽을 수 있는 숫자면 그 값. {@code 120.0}은 120 이고 {@code 4.5}·문자열·무한대·범위 밖은 null. */
    private static Long integral(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        BigDecimal decimal = decimal(value);
        if (decimal == null) {
            return null;
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * 숫자를 {@link BigDecimal}로. 실수는 유한할 때만 값이며({@code 1e999}는 Jackson 이 무한대로 읽는다) 꼬리 0 을 지우되
     * 지수 표기({@code 1.2E+7})가 되지 않게 소수 자리 0 이상으로 맞춘다 — FE 에는 {@code 12000000}으로 나가야 한다.
     */
    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (!Double.isFinite(d)) {
                return null;
            }
            BigDecimal v = BigDecimal.valueOf(d).stripTrailingZeros();
            return v.scale() < 0 ? v.setScale(0) : v;
        }
        return value instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    /** 문자열 필드. 없거나 공백뿐이면 null — 명세의 "string 또는 null" 에서 빈 문자열은 "없음"이다(global JsonNodes.text 와 같은 규칙). */
    private static String text(Map<?, ?> node, String field) {
        return node.get(field) instanceof String s && !s.isBlank() ? s : null;
    }

    /** 숫자만 값이다 — 문자열 "12000" 은 공급자 형식 변경 신호라 믿지 않는다(FE 어댑터의 Number.isFinite 와 같다). */
    private static BigDecimal number(Map<?, ?> node, String field) {
        return decimal(node.get(field));
    }

    /** 0 이상 int 범위의 정수만. 음수 리뷰 수는 공급자의 "모름" 표식일 수 있어 null 로 본다(전체 건수와 같은 규칙). */
    private static Integer integer(Map<?, ?> node, String field) {
        Long value = integral(node.get(field));
        return value != null && value >= 0 && value <= Integer.MAX_VALUE ? value.intValue() : null;
    }

    /**
     * 랜딩 URL → mylink 2단계. 랜딩 URL 은 HTTPS 의 myrealtrip.com(서브도메인 포함)이어야 하고, 그래야 mylink 의
     * {@code targetUrl}로 넘긴다 — 공급자가 준 값이라도 검증 없이 다음 호출의 입력으로 쓰지 않는다(명세 §처리 규칙).
     * 공급자가 조건·노선을 거절한 400·422 는 사용자가 고칠 수 있는 입력 문제라 400 으로 구분해 낸다(명세 §처리 규칙).
     * 404 는 경로·호스트 설정 문제일 수 있어 다른 경로와 같이 502 로 둔다 — 사용자 입력 오류로 숨기면 운영이 못 본다.
     */
    @Override
    public String createFlightLink(FlightSearch search) {
        Objects.requireNonNull(search, "search");
        String landingUrl = post(FLIGHT_LANDING_URL_PATH, Map.of(
                "depAirportCd", search.depAirportCd(),
                "arrAirportCd", search.arrAirportCd(),
                "depDate", search.depDate().toString(),
                "arrDate", search.arrDate().toString(),
                "adult", search.adult(),
                "tripTypeCd", ROUND_TRIP), LANDING_URL_TYPE, ErrorCode.UNSUPPORTED_FLIGHT_SEARCH);
        if (!MyRealTripUrls.isProductUrl(landingUrl)) {
            throw invalidResponse(FLIGHT_LANDING_URL_PATH, "랜딩 URL 이 HTTPS myrealtrip.com 이 아님: " + truncate(landingUrl));
        }
        return mylink(landingUrl);
    }

    @Override
    public String createProductLink(String targetUrl) {
        Objects.requireNonNull(targetUrl, "targetUrl");
        if (!MyRealTripUrls.isProductUrl(targetUrl)) {
            // 서비스가 같은 판정으로 400 을 먼저 내지만, 검증되지 않은 URL 을 파트너 본문에 싣는 일은 어댑터에서도 막는다.
            // 두 판정이 어긋나도 500 이 아니라 명세의 400 이어야 하므로 BusinessException 이다.
            throw new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST);
        }
        return mylink(targetUrl);
    }

    /** 고정된 mylink API 에 {@code targetUrl}을 본문 값으로만 전달한다. 결과는 HTTPS 의 myrealt.rip 만 믿는다. */
    private String mylink(String targetUrl) {
        PartnerMylinkData data = post(MYLINK_PATH, Map.of("targetUrl", targetUrl), MYLINK_TYPE);
        if (!MyRealTripUrls.isShortLink(data.mylink())) {
            throw invalidResponse(MYLINK_PATH, "mylink 가 HTTPS myrealt.rip 이 아님: " + truncate(data.mylink()));
        }
        return data.mylink();
    }

    private <T> T post(String path, Map<String, Object> body, ParameterizedTypeReference<PartnerEnvelope<T>> type) {
        return post(path, body, type, null);
    }

    /**
     * @param rejectedInputCode 공급자가 요청 내용을 거절한 400·422 를 이 코드(400)로 낼 때 지정. null 이면 502.
     *                          BE 가 입력을 전부 검증한 뒤의 거절이므로 계약 변경일 수도 있다 — WARN 에 본문을 남긴다.
     */
    private <T> T post(String path, Map<String, Object> body, ParameterizedTypeReference<PartnerEnvelope<T>> type,
                       ErrorCode rejectedInputCode) {
        String apiKey = properties.apiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("마이리얼트립 파트너 키가 설정되지 않았다(MY_REAL_TRIP_API_KEY)");
            throw new BusinessException(ErrorCode.MYREALTRIP_NOT_CONFIGURED);
        }
        try {
            PartnerEnvelope<T> envelope = restClient.post()
                    .uri(baseUrl + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
            if (envelope == null) {
                throw invalidResponse(path, "본문 없음");
            }
            if (!envelope.isSuccess()) {
                Integer partnerStatus = envelope.result() == null ? null : envelope.result().status();
                if (rejectedInputCode != null && partnerStatus != null && (partnerStatus == 400 || partnerStatus == 422)) {
                    // 봉투 안의 status 는 공급자 자체 상태다 — HTTP 200 으로 감싼 거절도 같은 400 으로 가른다.
                    log.warn("마이리얼트립 {} 요청 조건 거절(봉투 result.status={}, message={})",
                            path, partnerStatus, truncate(envelope.result().message()));
                    throw new BusinessException(rejectedInputCode);
                }
                // FE 어댑터와 같은 판정 — data 가 null 인 것도 실패다(결과 없음은 data.airports: [] 로 온다).
                String partnerMessage = envelope.result() == null ? null : envelope.result().message();
                throw invalidResponse(path, (envelope.data() == null ? "data 없음, " : "")
                        + "result.status=" + partnerStatus + (partnerMessage == null ? "" : ", message=" + truncate(partnerMessage)));
            }
            return envelope.data();
        } catch (RestClientResponseException e) {
            throw fromHttpError(path, e, rejectedInputCode);
        } catch (ResourceAccessException e) {
            if (isReadTimeout(e)) {
                log.error("마이리얼트립 {} 응답 시간 초과", path, e);
                throw new BusinessException(ErrorCode.MYREALTRIP_PARTNER_TIMEOUT, e);
            }
            // 연결 실패·연결 타임아웃은 "응답 지연"이 아니라 "호출 실패"다 — 미등록 송신 IP 가 SYN 을 버리는 경우가 여기다.
            log.error("마이리얼트립 {} 호출 실패(연결)", path, e);
            throw new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR, e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 본문 파싱 실패 등 — 공급자 응답을 신뢰할 수 없으므로 빈 결과로 숨기지 않는다(명세 §처리 규칙).
            log.error("마이리얼트립 {} 응답 처리 실패", path, e);
            throw new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR, e);
        }
    }

    private BusinessException fromHttpError(String path, RestClientResponseException e, ErrorCode rejectedInputCode) {
        int status = e.getStatusCode().value();
        if (rejectedInputCode != null && (status == 400 || status == 422)) {
            log.warn("마이리얼트립 {} 요청 조건 거절(지원하지 않는 조건이거나 파트너 계약 변경): {} {}",
                    path, status, truncate(e.getResponseBodyAsString()));
            return new BusinessException(rejectedInputCode, e);
        }
        if (status == 401 || status == 403) {
            // BE 의 파트너 키 또는 등록 IP 문제. 사용자의 로그인 만료가 아니므로 401 로 내지 않는다.
            log.error("마이리얼트립 {} 인증/IP 거절: {} (파트너 키·송신 IP 등록을 확인)", path, status);
            return new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR, e);
        }
        if (status == 429) {
            OptionalLong seconds = RetryAfterHeader.seconds(e.getResponseHeaders());
            Long retryAfter = seconds.isPresent() ? seconds.getAsLong() : null;
            log.warn("마이리얼트립 {} 요청 한도 초과(파트너), retryAfter={}s", path, retryAfter);
            return new RateLimitExceededException(ErrorCode.MYREALTRIP_RATE_LIMITED, retryAfter, e);
        }
        log.error("마이리얼트립 {} 호출 실패: {} {}", path, status, truncate(e.getResponseBodyAsString()));
        return new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR, e);
    }

    private static String truncate(String body) {
        if (body == null || body.length() <= ERROR_BODY_LOG_LIMIT) {
            return body;
        }
        return body.substring(0, ERROR_BODY_LOG_LIMIT) + "...(" + body.length() + " chars)";
    }

    private BusinessException invalidResponse(String path, String reason) {
        log.error("마이리얼트립 {} 응답이 잘못됐다: {}", path, reason);
        return new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR);
    }

    /**
     * 응답(read) 타임아웃인가 — 504 는 "파트너가 받았는데 답이 늦다"에만 쓴다. 이 클라이언트의 요청 팩토리는
     * {@code SimpleClientHttpRequestFactory}(HttpURLConnection)이고, 그것은 연결 타임아웃도 {@link SocketTimeoutException}
     * ("Connect timed out")으로 던지므로 메시지로 단계를 가른다. 연결 단계 실패는 502(호출 실패)다.
     * 팩토리를 JDK HttpClient 로 바꾸면 예외 타입이 달라지므로 이 판정도 같이 바꿔야 한다.
     */
    private static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                String message = t.getMessage();
                return message == null || !message.toLowerCase(Locale.ROOT).contains("connect");
            }
        }
        return false;
    }
}
