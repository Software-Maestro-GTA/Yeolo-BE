package com.soma.yeolo.location.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.location.dto.CityListResponse;
import com.soma.yeolo.location.dto.CountryListResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse;
import com.soma.yeolo.location.service.LocationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 국가·도시 기준 데이터 조회 (API-LOC-1/2/3). 명세상 인증이 필요 없는 공개 API 다
 * ({@code SecurityConfig}의 {@code /api/locations/**} 참고).
 *
 * <p>2026-10 개정으로 자동완성 두 개({@code /countries/autocomplete}, {@code /cities/autocomplete})가
 * 전체 목록 두 개와 키워드 검색 하나로 바뀌었다. 쿼리 파라미터는 모두 {@code required=false}로 받고
 * 서비스가 명세의 400 문구로 거절한다 — 스프링의 기본 누락 응답은 명세 봉투가 아니다.
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationQueryService locationQueryService;

    @GetMapping("/countries")
    public ApiResponse<CountryListResponse> listCountries() {
        return ApiResponse.success("국가 목록 조회 성공", locationQueryService.listCountries());
    }

    @GetMapping("/cities")
    public ApiResponse<CityListResponse> listCities(@RequestParam(required = false) String countryName) {
        return ApiResponse.success("도시 목록 조회 성공", locationQueryService.listCities(countryName));
    }

    @GetMapping("/search")
    public ApiResponse<LocationSearchResponse> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.success("국가·도시 검색 성공", locationQueryService.search(keyword));
    }
}
