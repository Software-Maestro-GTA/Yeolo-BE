package com.soma.yeolo.location.controller;

import static org.mockito.ArgumentMatchers.eq;
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
import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.location.dto.CityListResponse;
import com.soma.yeolo.location.dto.CountryListResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse;
import com.soma.yeolo.location.service.LocationLanguageResolver;
import com.soma.yeolo.location.service.LocationQueryService;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API-LOC-1/2/3 (2026-10 개정) — 경로·봉투·메시지·에러 코드를 명세대로 고정한다. 인증은 필요 없다.
 * 표시명 언어는 {@code Accept-Language}와 (토큰이 있으면) 사용자 식별자를 {@code LocationLanguageResolver}에
 * 넘겨 정한다 — 순서 규칙 자체는 {@code LocationLanguageResolverTest}가 고정한다.
 */
@WebMvcTest(LocationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class})
class LocationControllerTest {

    private static final LocationLanguage KO = LocationLanguage.KO;
    private static final LocationLanguage EN = LocationLanguage.EN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationQueryService locationQueryService;

    @MockitoBean
    private LocationLanguageResolver languageResolver;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    @org.junit.jupiter.api.BeforeEach
    void defaultLanguage() {
        // 리졸버 규칙은 별도 테스트. 여기서는 헤더 없음·비로그인이면 ko, 그 밖은 개별 테스트가 지정한다.
        when(languageResolver.resolve(isNull(), isNull())).thenReturn(KO);
    }

    @Test
    void 국가_전체_목록은_인증_없이_명세의_봉투로_응답한다() throws Exception {
        when(locationQueryService.listCountries(KO)).thenReturn(new CountryListResponse(
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
        when(locationQueryService.listCities("일본", KO)).thenReturn(new CityListResponse(
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
        when(locationQueryService.listCities(isNull(), eq(KO)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_COUNTRY_NAME));

        mockMvc.perform(get("/api/locations/cities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("국가명을 입력해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 없는_국가면_404_봉투로_응답한다() throws Exception {
        when(locationQueryService.listCities("아틀란티스", KO))
                .thenThrow(new BusinessException(ErrorCode.COUNTRY_NOT_FOUND));

        mockMvc.perform(get("/api/locations/cities").param("countryName", "아틀란티스"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("해당 국가를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 검색은_국가별로_묶인_도시명_목록으로_응답한다() throws Exception {
        when(locationQueryService.search("다", KO)).thenReturn(new LocationSearchResponse(List.of(
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
        when(locationQueryService.search("없는곳", KO)).thenReturn(new LocationSearchResponse(List.of()));

        mockMvc.perform(get("/api/locations/search").param("keyword", "없는곳"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results", Matchers.hasSize(0)));
    }

    @Test
    void 키워드가_없으면_명세의_400_문구로_응답한다() throws Exception {
        when(locationQueryService.search(isNull(), eq(KO)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_SEARCH_KEYWORD));

        mockMvc.perform(get("/api/locations/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("검색 키워드를 입력해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    /** 헤더와 (있으면) 사용자 식별자를 리졸버에 넘기고, 돌려받은 언어로 서비스를 부른다. */
    @Test
    void Accept_Language가_영어면_영문_표시명으로_응답한다() throws Exception {
        when(languageResolver.resolve(eq("en-US,en;q=0.9,ko;q=0.5"), isNull())).thenReturn(EN);
        when(languageResolver.resolve(eq("en"), isNull())).thenReturn(EN);
        when(languageResolver.resolve(eq("en-GB"), isNull())).thenReturn(EN);
        when(locationQueryService.listCountries(EN)).thenReturn(new CountryListResponse(
                List.of(new CountryListResponse.Country("Japan"), new CountryListResponse.Country("South Korea"))));
        when(locationQueryService.listCities("Japan", EN)).thenReturn(new CityListResponse(
                List.of(new CityListResponse.City("Tokyo"))));
        when(locationQueryService.search("to", EN)).thenReturn(new LocationSearchResponse(List.of(
                new LocationSearchResponse.CountryResult("Japan", List.of(new LocationSearchResponse.City("Tokyo"))))));

        mockMvc.perform(get("/api/locations/countries").header("Accept-Language", "en-US,en;q=0.9,ko;q=0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countries[0].countryName").value("Japan"));
        mockMvc.perform(get("/api/locations/cities").param("countryName", "Japan").header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].cityName").value("Tokyo"));
        mockMvc.perform(get("/api/locations/search").param("keyword", "to").header("Accept-Language", "en-GB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].countryName").value("Japan"));
    }

    @Test
    void Accept_Language가_없으면_헤더_없음으로_리졸버에_넘긴다() throws Exception {
        when(locationQueryService.listCountries(KO)).thenReturn(new CountryListResponse(
                List.of(new CountryListResponse.Country("대한민국"))));

        mockMvc.perform(get("/api/locations/countries"))
                .andExpect(jsonPath("$.data.countries[0].countryName").value("대한민국"));
    }

    /** 인증이 필요 없는 API 지만 토큰이 오면 사용자가 식별돼 저장된 언어 설정(API-USER-4)이 표시명에 반영된다. */
    @Test
    void 로그인_사용자는_식별자를_리졸버에_넘겨_저장된_언어_설정을_반영한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        when(languageResolver.resolve(eq("ko"), eq(userId))).thenReturn(EN);
        when(locationQueryService.listCities("일본", EN)).thenReturn(new CityListResponse(
                List.of(new CityListResponse.City("Tokyo"))));

        mockMvc.perform(get("/api/locations/cities").param("countryName", "일본")
                        .header("Authorization", "Bearer valid-token").header("Accept-Language", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities[0].cityName").value("Tokyo"));
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
