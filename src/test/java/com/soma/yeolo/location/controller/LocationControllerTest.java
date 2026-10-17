package com.soma.yeolo.location.controller;

import static org.mockito.ArgumentMatchers.isNull;
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
import com.soma.yeolo.location.dto.CityListResponse;
import com.soma.yeolo.location.dto.CountryListResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse;
import com.soma.yeolo.location.service.LocationQueryService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API-LOC-1/2/3 (2026-10 개정) — 경로·봉투·메시지·에러 코드를 명세대로 고정한다. 인증은 필요 없다. */
@WebMvcTest(LocationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class})
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationQueryService locationQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    @Test
    void 국가_전체_목록은_인증_없이_명세의_봉투로_응답한다() throws Exception {
        when(locationQueryService.listCountries()).thenReturn(new CountryListResponse(
                List.of(new CountryListResponse.Country("대한민국"), new CountryListResponse.Country("일본"))));

        mockMvc.perform(get("/api/locations/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("국가 목록 조회 성공"))
                .andExpect(jsonPath("$.data.countries[0].countryName").value("대한민국"))
                .andExpect(jsonPath("$.data.countries[1].countryName").value("일본"))
                // 명세 JSON 스키마에 없는 필드는 내려가지 않는다.
                .andExpect(jsonPath("$.data.countries[0].countryId").doesNotExist());
    }

    @Test
    void 국가별_도시_목록은_countryName으로_조회한다() throws Exception {
        when(locationQueryService.listCities("일본")).thenReturn(new CityListResponse(
                List.of(new CityListResponse.City("도쿄"), new CityListResponse.City("오사카"))));

        mockMvc.perform(get("/api/locations/cities").param("countryName", "일본"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("도시 목록 조회 성공"))
                .andExpect(jsonPath("$.data.cities", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.cities[0].cityName").value("도쿄"))
                .andExpect(jsonPath("$.data.cities[0].cityId").doesNotExist());
    }

    @Test
    void countryName이_없으면_명세의_400_봉투로_응답한다() throws Exception {
        when(locationQueryService.listCities(isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_COUNTRY_NAME));

        mockMvc.perform(get("/api/locations/cities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("국가명을 입력해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 없는_국가면_404_봉투로_응답한다() throws Exception {
        when(locationQueryService.listCities("아틀란티스"))
                .thenThrow(new BusinessException(ErrorCode.COUNTRY_NOT_FOUND));

        mockMvc.perform(get("/api/locations/cities").param("countryName", "아틀란티스"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("해당 국가를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 검색은_국가별로_묶인_도시명_목록으로_응답한다() throws Exception {
        when(locationQueryService.search("다")).thenReturn(new LocationSearchResponse(List.of(
                new LocationSearchResponse.CountryResult("대한민국",
                        List.of(new LocationSearchResponse.City("대구"))),
                new LocationSearchResponse.CountryResult("일본",
                        List.of(new LocationSearchResponse.City("다카마쓰"))))));

        mockMvc.perform(get("/api/locations/search").param("keyword", "다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("국가·도시 검색 성공"))
                .andExpect(jsonPath("$.data.results", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.results[0].countryName").value("대한민국"))
                .andExpect(jsonPath("$.data.results[0].cities[0].cityName").value("대구"))
                .andExpect(jsonPath("$.data.results[1].countryName").value("일본"));
    }

    @Test
    void 검색_결과가_없으면_빈_배열이다() throws Exception {
        when(locationQueryService.search("없는곳")).thenReturn(new LocationSearchResponse(List.of()));

        mockMvc.perform(get("/api/locations/search").param("keyword", "없는곳"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results", Matchers.hasSize(0)));
    }

    @Test
    void 키워드가_없으면_명세의_400_문구로_응답한다() throws Exception {
        when(locationQueryService.search(isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SEARCH_KEYWORD));

        mockMvc.perform(get("/api/locations/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("검색 키워드를 입력해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    /** 2026-10 개정으로 폐기된 자동완성 경로는 더 이상 없다. */
    @Test
    void 폐기된_자동완성_경로는_404다() throws Exception {
        mockMvc.perform(get("/api/locations/countries/autocomplete").param("keyword", "일"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/locations/cities/autocomplete").param("keyword", "오사"))
                .andExpect(status().isNotFound());
    }
}
