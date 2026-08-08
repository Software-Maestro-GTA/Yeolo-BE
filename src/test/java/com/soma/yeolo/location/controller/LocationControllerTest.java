package com.soma.yeolo.location.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.GlobalExceptionHandler;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.location.dto.CityAutocompleteResponse;
import com.soma.yeolo.location.dto.CountryAutocompleteResponse;
import com.soma.yeolo.location.service.LocationAutocompleteService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 자동완성 API의 응답 계약(API-LOC-1 / API-LOC-2)을 검증한다.
 *
 * <p>여기서만 드러나는 것들이다 — 명세의 {@code {status, message, data}} 봉투와 중첩 키
 * ({@code data.countries} / {@code data.cities}), 결과 없음이 {@code null}이 아니라 빈 배열이라는 점,
 * 그리고 <b>토큰 없이 호출된다</b>는 점(명세상 "인증 필요: N").
 */
@WebMvcTest(LocationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class})
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationAutocompleteService locationAutocompleteService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    @Test
    void 국가_자동완성은_인증_없이_명세의_봉투로_응답한다() throws Exception {
        when(locationAutocompleteService.searchCountries("대한", null))
                .thenReturn(new CountryAutocompleteResponse(
                        List.of(new CountryAutocompleteResponse.Country("KR", "대한민국"))));

        mockMvc.perform(get("/api/locations/countries/autocomplete").param("keyword", "대한"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("국가 자동완성 조회 성공"))
                .andExpect(jsonPath("$.data.countries[0].countryId").value("KR"))
                .andExpect(jsonPath("$.data.countries[0].countryNameKo").value("대한민국"));
    }

    @Test
    void 도시_자동완성은_소속_국가를_함께_응답한다() throws Exception {
        when(locationAutocompleteService.searchCities("서울", 5))
                .thenReturn(new CityAutocompleteResponse(List.of(
                        new CityAutocompleteResponse.City("1835848", "서울특별시", "KR", "대한민국"))));

        mockMvc.perform(get("/api/locations/cities/autocomplete")
                        .param("keyword", "서울").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("도시 자동완성 조회 성공"))
                .andExpect(jsonPath("$.data.cities[0].cityId").value("1835848"))
                .andExpect(jsonPath("$.data.cities[0].cityNameKo").value("서울특별시"))
                .andExpect(jsonPath("$.data.cities[0].countryId").value("KR"))
                .andExpect(jsonPath("$.data.cities[0].countryNameKo").value("대한민국"));

        verify(locationAutocompleteService).searchCities("서울", 5);
    }

    @Test
    void 결과가_없으면_빈_배열이_내려간다() throws Exception {
        when(locationAutocompleteService.searchCities(eq("없는도시"), isNull()))
                .thenReturn(new CityAutocompleteResponse(List.of()));

        mockMvc.perform(get("/api/locations/cities/autocomplete").param("keyword", "없는도시"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities").isArray())
                .andExpect(jsonPath("$.data.cities", Matchers.hasSize(0)));
    }

    @Test
    void 검색어가_없으면_명세의_400_문구로_응답한다() throws Exception {
        when(locationAutocompleteService.searchCountries(isNull(), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_COUNTRY_KEYWORD));

        mockMvc.perform(get("/api/locations/countries/autocomplete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("국가 검색어를 확인해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void limit이_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/locations/cities/autocomplete")
                        .param("keyword", "서울").param("limit", "많이"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
