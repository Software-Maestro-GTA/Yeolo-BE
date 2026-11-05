package com.soma.yeolo.myrealtrip.client;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.client.dto.PartnerAirportsData;
import com.soma.yeolo.myrealtrip.client.dto.PartnerEnvelope;
import com.soma.yeolo.myrealtrip.client.dto.PartnerRegionsData;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.Region;
import com.soma.yeolo.global.client.RetryAfterHeader;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private <T> T post(String path, Map<String, Object> body, ParameterizedTypeReference<PartnerEnvelope<T>> type) {
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
                // FE 어댑터와 같은 판정 — data 가 null 인 것도 실패다(결과 없음은 data.airports: [] 로 온다).
                throw invalidResponse(path, envelope.data() == null ? "data 없음"
                        : "result.status=" + envelope.result().status());
            }
            return envelope.data();
        } catch (RestClientResponseException e) {
            throw fromHttpError(path, e);
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

    private BusinessException fromHttpError(String path, RestClientResponseException e) {
        int status = e.getStatusCode().value();
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
