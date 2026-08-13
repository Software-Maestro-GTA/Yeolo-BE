package com.soma.yeolo.location.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.domain.SearchKeys;
import com.soma.yeolo.location.dto.CityAutocompleteResponse;
import com.soma.yeolo.location.dto.CountryAutocompleteResponse;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 국가·도시 자동완성 조회 (API-LOC-1 / API-LOC-2 / FUN-5).
 *
 * <p>검색어를 검색 키 규칙({@link SearchKeys})으로 정규화한 뒤, 초성만 입력됐으면 초성 컬럼을,
 * 아니면 이름 컬럼을 대상으로 조회한다. 적재 시점과 같은 함수로 키를 만들기 때문에 저장된 값과
 * 검색어가 같은 규칙 위에서 비교된다.
 *
 * <p>후보가 없으면 예외가 아니라 빈 목록이다(인수 기준). 400은 "검색할 수 없는 입력"일 때만 —
 * 검색어가 비었거나, 구분자만 있어 검색 키가 남지 않거나, 비정상적으로 긴 경우다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationAutocompleteService {

    /** {@code limit} 미지정 시 후보 개수. 자동완성 드롭다운이 한 화면에 담는 정도. */
    private static final int DEFAULT_LIMIT = 10;

    /** {@code limit} 상한. 자동완성은 타자마다 호출되므로 한 번에 퍼올 수 있는 양을 묶어 둔다. */
    private static final int MAX_LIMIT = 50;

    /**
     * 검색 키 길이 상한. 실제 지명은 이보다 짧으므로 넘어가면 검색 의도가 아니다 —
     * 긴 문자열로 LIKE 스캔을 유발하는 요청을 조회 전에 끊는다.
     */
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;

    /** 국가 자동완성 (API-LOC-1). */
    public CountryAutocompleteResponse searchCountries(String keyword, Integer limit) {
        String key = requireSearchKey(keyword, ErrorCode.INVALID_COUNTRY_KEYWORD);
        Pageable page = pageOf(limit);

        List<CountryEntity> found = SearchKeys.isChosungOnly(key)
                ? countryRepository.searchByChosung(LikePatterns.prefix(key), page)
                : countryRepository.searchByName(LikePatterns.contains(key), LikePatterns.prefix(key), page);

        return CountryAutocompleteResponse.from(found);
    }

    /**
     * 도시 자동완성 (API-LOC-2). {@code country}를 주면 그 국가의 도시만, 없으면 전 세계에서 찾는다.
     *
     * <p><b>{@code country}는 국가 식별자(ISO 3166-1 alpha-2)와 국가 한국어명을 모두 받는다.</b>
     * 명세가 {@code "country": "string(optional)"} 이상을 규정하지 않아 BE에서 정한 것이다. 앞의
     * 국가 자동완성(API-LOC-1)이 FE에 주는 식별자는 {@code countryId}(alpha-2)이므로 그것이 기본
     * 경로지만, 화면에 보이는 것은 국가명이라 그대로 실어 보내기 쉽다. 둘 중 하나로만 받으면 다른
     * 쪽을 보냈을 때 <b>400이 아니라 빈 목록</b>이 나가 원인을 알기 어렵다 — 조건 하나를 더 두는
     * 비용으로 그 함정을 없앤다.
     *
     * <p>알 수 없는 국가 값은 거절하지 않고 빈 목록으로 둔다. 국가 목록은 자동완성이 제공하는
     * 기준 데이터라 "검색할 수 없는 입력"(400)이 아니라 "후보 없음"에 해당한다.
     */
    public CityAutocompleteResponse searchCities(String country, String keyword, Integer limit) {
        String key = requireSearchKey(keyword, ErrorCode.INVALID_CITY_KEYWORD);
        Pageable page = pageOf(limit);
        String countryFilter = normalizeCountry(country);

        List<CityEntity> found;
        if (countryFilter == null) {
            found = SearchKeys.isChosungOnly(key)
                    ? cityRepository.searchByChosung(LikePatterns.prefix(key), page)
                    : cityRepository.searchByName(LikePatterns.contains(key), LikePatterns.prefix(key), page);
        } else {
            // countryId는 대문자로 적재되므로 alpha-2 후보만 대문자로 맞춘다. 국가명은 원문 그대로 비교한다.
            String countryId = countryFilter.toUpperCase(Locale.ROOT);
            found = SearchKeys.isChosungOnly(key)
                    ? cityRepository.searchByChosungInCountry(LikePatterns.prefix(key), countryId, countryFilter, page)
                    : cityRepository.searchByNameInCountry(LikePatterns.contains(key), LikePatterns.prefix(key),
                            countryId, countryFilter, page);
        }

        return CityAutocompleteResponse.from(found);
    }

    /** 공백뿐인 국가 값은 미전송과 같이 취급한다(FE가 빈 파라미터를 붙이는 경우). */
    private String normalizeCountry(String country) {
        return StringUtils.hasText(country) ? country.strip() : null;
    }

    /**
     * 검색어를 검색 키로 바꾸고, 검색할 수 없는 입력이면 명세의 400으로 거절한다.
     *
     * <p>정규화 결과가 비는 경우(공백·기호만 입력)를 반드시 걸러야 한다. 그대로 두면 패턴이
     * {@code %%}가 되어 전체 행이 걸린다 — limit이 개수는 막아도 의미 없는 목록이 응답된다.
     */
    private String requireSearchKey(String keyword, ErrorCode errorCode) {
        String key = SearchKeys.normalize(keyword);
        if (key.isEmpty() || key.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(errorCode);
        }
        return key;
    }

    /** 잘못된 {@code limit}은 거절하지 않고 허용 범위로 맞춘다 — 검색어가 아니라 표시 개수라서. */
    private Pageable pageOf(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        return PageRequest.of(0, size);
    }
}
