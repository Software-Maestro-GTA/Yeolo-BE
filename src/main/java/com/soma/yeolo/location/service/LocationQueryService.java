package com.soma.yeolo.location.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.location.domain.SearchKeys;
import com.soma.yeolo.location.dto.CityListResponse;
import com.soma.yeolo.location.dto.CountryListResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse.City;
import com.soma.yeolo.location.dto.LocationSearchResponse.CountryResult;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 국가·도시 목록 조회와 키워드 검색 (API-LOC-1/2/3, 2026-10 개정).
 *
 * <p>자동완성(부분 일치·초성·limit)은 명세에서 사라졌다. 남은 것은 전체 목록 두 개와, 검색 버튼을
 * 눌렀을 때 한 번 부르는 접두어 검색 하나다. 세 API 모두 페이지네이션이 없고 결과 전체를 내린다.
 *
 * <p><b>언어.</b> 응답의 표시명은 {@link LocationLanguage}(요청 {@code Accept-Language}에서 컨트롤러가
 * 정한다)를 따르고, 이름 매칭은 언어와 무관하게 한국어·영문 검색 키를 모두 본다 — 명세 §언어 정책
 * "검색 입력 언어와 응답 표시 언어는 별개이며, 등록된 다국어 이름·별칭을 검색한 뒤 응답 언어에 맞는
 * 표시명을 반환한다". 그래서 {@code countryName=Japan}도 {@code countryName=일본}과 같은 국가다.
 *
 * <p>이름 비교는 적재 시 만든 검색 키({@code SearchKeys.normalize}: 발음 부호 제거·소문자화·공백과
 * 구분자 제거)로 하므로 영문 대소문자와 공백을 구분하지 않는다(명세 "영문 대소문자를 구분하지 않는다").
 *
 * <p><b>정렬은 전부 DB 에 맡긴다.</b> 세 API 가 같은 "표시명 오름차순"을 말하므로 한 비교 기준(DB
 * 콜레이션, 응답 언어의 표시명 열)만 써야 같은 국가의 도시 순서가 API 마다 달라지지 않는다. 자바에서
 * 다시 정렬하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationQueryService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String COUNTRY_ID = "countryId";

    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;

    /** 서비스가 지원하는 국가 전체, 표시명 오름차순 (API-LOC-1). */
    public CountryListResponse listCountries(LocationLanguage language) {
        return CountryListResponse.from(countryRepository.findAll(byName(language)), language);
    }

    /**
     * 국가명으로 지정한 국가의 도시 전체, 표시명 오름차순 (API-LOC-2). 국가명이 비면 400, 없는 국가면
     * 404, 국가는 있는데 등록 도시가 없으면 200과 빈 배열이다(명세). 국가명은 어느 언어로 줘도 된다.
     */
    public CityListResponse listCities(String countryName, LocationLanguage language) {
        String key = normalizeOrThrow(countryName, ErrorCode.INVALID_COUNTRY_NAME);
        CountryEntity country = countryRepository.findByAnySearchName(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUNTRY_NOT_FOUND));
        return CityListResponse.from(cityRepository.findByCountryId(country.getCountryId(), byName(language)),
                language);
    }

    /**
     * 국가·도시 키워드 검색 (API-LOC-3 §매칭 규칙).
     *
     * <ol>
     *   <li>키워드가 국가명과 완전히 일치하면 그 국가의 전체 도시를 돌려준다.</li>
     *   <li>아니면 국가명·도시명에 접두어 검색을 한다. 국가명이 매칭되면 그 국가의 전체 도시를, 도시명만
     *       매칭되면 소속 국가 아래 매칭된 도시만 담는다.</li>
     *   <li>같은 국가는 하나로 묶고 도시는 식별자 기준으로 중복을 없앤다. 국가명 매칭으로 전체 도시가
     *       들어간 국가에는 도시명 매칭을 다시 더하지 않는다.</li>
     *   <li>국가와 각 국가의 도시는 표시명 오름차순이다. 결과가 없으면 빈 배열이다.</li>
     * </ol>
     *
     * <p>질의 수는 키워드와 무관하게 최대 5회다(완전 일치, 국가 접두어, 도시 접두어, 국가 정렬 조회,
     * 전체 도시 일괄 조회). 국가마다 도시를 따로 묻지 않는다.
     */
    public LocationSearchResponse search(String keyword, LocationLanguage language) {
        String key = normalizeOrThrow(keyword, ErrorCode.INVALID_SEARCH_KEYWORD);
        Sort byName = byName(language);

        Optional<CountryEntity> exact = countryRepository.findByAnySearchName(key);
        if (exact.isPresent()) {
            CountryEntity country = exact.get();
            return new LocationSearchResponse(List.of(new CountryResult(country.displayName(language),
                    toCities(cityRepository.findByCountryId(country.getCountryId(), byName), language))));
        }

        String prefix = LikePatterns.prefix(key);
        // 국가명 매칭 → 그 국가의 전체 도시
        Set<String> fullCountryIds = new LinkedHashSet<>();
        for (CountryEntity country : countryRepository.searchByPrefix(prefix, byName)) {
            fullCountryIds.add(country.getCountryId());
        }
        // 도시명 매칭 → 소속 국가 아래 매칭 도시만. 전체 도시가 들어가는 국가는 건너뛰고, 도시는 식별자로 중복 제거.
        Map<String, Map<String, CityEntity>> partial = new LinkedHashMap<>();
        for (CityEntity city : cityRepository.searchByPrefix(prefix, Sort.by(COUNTRY_ID).and(byName))) {
            if (fullCountryIds.contains(city.getCountryId())) {
                continue;
            }
            partial.computeIfAbsent(city.getCountryId(), id -> new LinkedHashMap<>())
                    .putIfAbsent(city.getCityId(), city);
        }
        Set<String> allCountryIds = new LinkedHashSet<>(fullCountryIds);
        allCountryIds.addAll(partial.keySet());
        if (allCountryIds.isEmpty()) {
            return new LocationSearchResponse(List.of());
        }

        // 국가 순서와 전체 도시 목록은 DB 정렬로 한 번에 받는다.
        Map<String, List<City>> fullCities = new LinkedHashMap<>();
        if (!fullCountryIds.isEmpty()) {
            for (CityEntity city : cityRepository.findByCountryIdIn(fullCountryIds, Sort.by(COUNTRY_ID).and(byName))) {
                fullCities.computeIfAbsent(city.getCountryId(), id -> new ArrayList<>())
                        .add(new City(city.displayName(language)));
            }
        }
        List<CountryResult> results = new ArrayList<>();
        for (CountryEntity country : countryRepository.findByCountryIdIn(allCountryIds, byName)) {
            String id = country.getCountryId();
            List<City> cities = fullCountryIds.contains(id)
                    ? fullCities.getOrDefault(id, List.of())     // 국가가 매칭됐지만 등록 도시가 없으면 빈 배열(명세)
                    : toCities(partial.get(id).values(), language);
            results.add(new CountryResult(country.displayName(language), cities));
        }
        return new LocationSearchResponse(results);
    }

    /** 응답 언어의 표시명 오름차순. 두 엔티티가 같은 속성명({@code nameKo}·{@code nameEn})을 쓴다. */
    private static Sort byName(LocationLanguage language) {
        return Sort.by(language.property());
    }

    private List<City> toCities(Iterable<CityEntity> cities, LocationLanguage language) {
        List<City> result = new ArrayList<>();
        for (CityEntity city : cities) {
            result.add(new City(city.displayName(language)));
        }
        return result;
    }

    /** 앞뒤 공백을 제거하고 검색 키로 정규화한다. 키가 남지 않거나 지나치게 길면 400이다. */
    private String normalizeOrThrow(String raw, ErrorCode errorCode) {
        String key = SearchKeys.normalize(raw);
        if (key.isEmpty() || key.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(errorCode);
        }
        return key;
    }
}
