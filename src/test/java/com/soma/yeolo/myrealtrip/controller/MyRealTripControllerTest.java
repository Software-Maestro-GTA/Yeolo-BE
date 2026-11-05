package com.soma.yeolo.myrealtrip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.GlobalExceptionHandler;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.Region;
import com.soma.yeolo.myrealtrip.dto.AirportSearchResponse;
import com.soma.yeolo.myrealtrip.dto.RegionSearchResponse;
import com.soma.yeolo.myrealtrip.service.MyRealTripSearchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API-MRT-1/2 — 경로·인증·봉투·메시지·에러 코드를 명세대로 고정한다. 429 는 {@code Retry-After} 헤더까지 본다
 * (전역 핸들러의 {@code RateLimitExceededException} 처리).
 */
@WebMvcTest(MyRealTripController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class})
class MyRealTripControllerTest {

    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyRealTripSearchService searchService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() {
        when(jwtTokenProvider.parseAccessTokenUserId(TOKEN)).thenReturn(userId);
    }

    @Test
    void 토큰이_없으면_401_봉투이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "인천"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value((Object) null));
        mockMvc.perform(get("/api/myrealtrip/regions").param("keyword", "도쿄").param("isDomestic", "false"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(searchService);
    }

    @Test
    void 공항_검색_성공_봉투() throws Exception {
        when(searchService.searchAirports(userId, "인천")).thenReturn(AirportSearchResponse.from(List.of(
                new Airport("ICN", "인천국제공항", "Incheon International Airport"),
                new Airport("GMP", null, null))));

        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "인천")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("공항 검색 성공"))
                .andExpect(jsonPath("$.data.airports[0].airport.code").value("ICN"))
                .andExpect(jsonPath("$.data.airports[0].airport.koName").value("인천국제공항"))
                .andExpect(jsonPath("$.data.airports[0].airport.enName").value("Incheon International Airport"))
                .andExpect(jsonPath("$.data.airports[1].airport.code").value("GMP"))
                // 공급자 미제공 이름은 생략한다(명세 §처리 규칙).
                .andExpect(jsonPath("$.data.airports[1].airport.koName").doesNotExist());
    }

    @Test
    void 숙박_지역_검색_성공_봉투() throws Exception {
        when(searchService.searchRegions(userId, "도쿄", "false")).thenReturn(RegionSearchResponse.from(List.of(
                new Region(12345, "도쿄", "일본"), new Region(67890, "도쿄역", null))));

        mockMvc.perform(get("/api/myrealtrip/regions").param("keyword", "도쿄").param("isDomestic", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("숙박 지역 검색 성공"))
                .andExpect(jsonPath("$.data.regions[0].regionId").value(12345))
                .andExpect(jsonPath("$.data.regions[0].name").value("도쿄"))
                .andExpect(jsonPath("$.data.regions[0].subName").value("일본"))
                .andExpect(jsonPath("$.data.regions[1].subName").doesNotExist());
    }

    @Test
    void 입력값_오류는_명세_문구의_400이다() throws Exception {
        when(searchService.searchRegions(eq(userId), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST));

        // 파라미터 누락도 스프링 기본 응답이 아니라 서비스의 명세 봉투로 나간다(required=false).
        mockMvc.perform(get("/api/myrealtrip/regions").param("keyword", "도쿄")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청 값을 확인해주세요."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void 요청_한도_초과는_429_봉투와_Retry_After_헤더다() throws Exception {
        when(searchService.searchAirports(userId, "인천"))
                .thenThrow(new RateLimitExceededException(ErrorCode.MYREALTRIP_RATE_LIMITED, 42L));

        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "인천")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void 재시도_시점을_모르는_429는_헤더_없이_봉투만_낸다() throws Exception {
        when(searchService.searchAirports(userId, "인천"))
                .thenThrow(new RateLimitExceededException(ErrorCode.MYREALTRIP_RATE_LIMITED, null));

        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "인천")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 파트너_실패는_명세의_502_504_500으로_나간다() throws Exception {
        when(searchService.searchAirports(userId, "a"))
                .thenThrow(new BusinessException(ErrorCode.MYREALTRIP_PARTNER_ERROR));
        when(searchService.searchAirports(userId, "b"))
                .thenThrow(new BusinessException(ErrorCode.MYREALTRIP_PARTNER_TIMEOUT));
        when(searchService.searchAirports(userId, "c"))
                .thenThrow(new BusinessException(ErrorCode.MYREALTRIP_NOT_CONFIGURED));

        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "a")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "b")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504));
        mockMvc.perform(get("/api/myrealtrip/airports").param("keyword", "c")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
