package com.soma.yeolo.location.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.location.dto.CityAutocompleteResponse;
import com.soma.yeolo.location.dto.CountryAutocompleteResponse;
import com.soma.yeolo.location.service.LocationAutocompleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 국가·도시 자동완성 API (API-LOC-1 / API-LOC-2). 코스 생성 화면의 지역 선택에 쓰인다(FUN-5).
 *
 * <p>두 엔드포인트 모두 명세의 "인증 필요: N"에 따라 공개다(SecurityConfig). 로그인 전 화면에서도
 * 지역을 훑어볼 수 있고, 응답은 공개 기준 정보라 사용자별로 달라지지 않는다. API-LOC-2 명세에 적힌
 * 401 은 그래서 발생하지 않는다 — 명세의 자기모순을 공개 쪽으로 정리한 결과다(SecurityConfig 주석).
 *
 * <p>{@code keyword}를 {@code required=false}로 받는다. 필수로 두면 파라미터 누락이 스프링의
 * {@code MissingServletRequestParameterException}이 되어 명세와 다른 문구로 응답한다 —
 * 서비스가 검증해 명세의 400 메시지("… 검색어를 확인해주세요.")로 통일한다.
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationAutocompleteService locationAutocompleteService;

    /** 국가 자동완성 조회 (API-LOC-1). */
    @GetMapping("/countries/autocomplete")
    public ApiResponse<CountryAutocompleteResponse> autocompleteCountries(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success("국가 자동완성 조회 성공",
                locationAutocompleteService.searchCountries(keyword, limit));
    }

    /** 도시 자동완성 조회 (API-LOC-2). */
    @GetMapping("/cities/autocomplete")
    public ApiResponse<CityAutocompleteResponse> autocompleteCities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success("도시 자동완성 조회 성공",
                locationAutocompleteService.searchCities(keyword, limit));
    }
}
