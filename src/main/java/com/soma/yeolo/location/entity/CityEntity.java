package com.soma.yeolo.location.entity;

import com.soma.yeolo.location.domain.SearchKeys;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도시 기준 정보 (API-LOC-2/3, 도시 사진 폴백). GeoNames {@code cities15000}(인구 15,000명 이상 도시)와
 * 한국어 대체 이름을 근거로 부팅 시 적재한다({@code LocationSeedLoader}).
 *
 * <p>{@link CountryEntity}와 같은 이유로 병합형 참조 데이터다(docs/architecture.md §1-1).
 *
 * <p><b>국가명을 비정규화해 함께 들고 있는다.</b> 검색(API-LOC-3)이 도시명 매칭 결과를 소속 국가로
 * 묶어 돌려주는데, 두 테이블은 항상 같은 데이터셋으로 함께 적재되므로 조인해서 얻을 값이 조회 시점마다
 * 달라질 여지가 없다.
 *
 * <p>{@code population}은 동명 도시를 가르는 기준이다. 국가 안에서 같은 이름이 여럿이면(미국의
 * Springfield 등) 인구가 많은 도시를 고른다(사진 조회). 2026-10 개정으로 자동완성 순위에는 쓰지 않는다.
 *
 * <p>인덱스: 접두어·완전 일치 검색이 쓰는 {@code search_name}, 국가별 전체 목록이 거르는
 * {@code country_id}. {@code search_chosung}은 초성 검색이 명세에서 빠져 더 이상 조회에 쓰지 않지만
 * dev 의 {@code ddl-auto=update}가 컬럼을 지우지 않고 NOT NULL 이라 값은 계속 채운다(정리는
 * {@code docs/ddl/cities.sql}). prod 의 LIKE 인덱스는 {@code text_pattern_ops} opclass 로 만든다.
 */
@Getter
@Entity
@Table(name = "cities", indexes = {
        @Index(name = "idx_cities_search_chosung", columnList = "search_chosung"),
        @Index(name = "idx_cities_search_name", columnList = "search_name"),
        @Index(name = "idx_cities_country_id", columnList = "country_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CityEntity {

    /** GeoNames {@code geonameid}를 문자열로 사용한다. 명세의 {@code cityId}. */
    @Id
    @Column(name = "city_id", length = 32, nullable = false, updatable = false)
    private String cityId;

    @Column(name = "name_ko", nullable = false, columnDefinition = "text")
    private String nameKo;

    @Column(name = "search_name", nullable = false, columnDefinition = "text")
    private String searchName;

    @Column(name = "search_chosung", nullable = false, columnDefinition = "text")
    private String searchChosung;

    @Column(name = "country_id", length = 2, nullable = false)
    private String countryId;

    @Column(name = "country_name_ko", nullable = false, columnDefinition = "text")
    private String countryNameKo;

    @Column(name = "population", nullable = false)
    private long population;

    /** 데이터셋 한 행 → 엔티티. 검색 키 파생은 {@link CountryEntity#of}와 같은 규칙을 쓴다. */
    public static CityEntity of(String cityId, String nameKo, String countryId,
                                String countryNameKo, long population) {
        CityEntity entity = new CityEntity();
        entity.cityId = cityId;
        entity.nameKo = nameKo;
        entity.searchName = SearchKeys.normalize(nameKo);
        entity.searchChosung = SearchKeys.chosung(nameKo);
        entity.countryId = countryId;
        entity.countryNameKo = countryNameKo;
        entity.population = population;
        return entity;
    }
}
