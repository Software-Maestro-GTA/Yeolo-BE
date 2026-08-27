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
 * 도시 기준 정보 (API-LOC-2). GeoNames {@code cities15000}(인구 15,000명 이상 도시)와 한국어
 * 대체 이름을 근거로 부팅 시 적재한다({@code LocationSeedLoader}).
 *
 * <p>{@link CountryEntity}와 같은 이유로 병합형 참조 데이터다(docs/architecture.md §1-1).
 *
 * <p><b>국가명을 비정규화해 함께 들고 있는다.</b> 명세의 도시 응답은 {@code countryNameKo}를
 * 포함하는데, 두 테이블은 항상 같은 데이터셋으로 함께 적재되므로 조인해서 얻을 값이 조회 시점마다
 * 달라질 여지가 없다. 자동완성은 타자마다 호출되는 경로라 조인을 없애는 편이 낫다.
 *
 * <p>{@code population}은 정렬 기준이다. 자동완성에서 "도쿄"보다 동명의 소도시가 위에 오면 쓸모가
 * 없으므로, 같은 조건이면 인구가 많은 도시를 먼저 보여준다.
 *
 * <p>인덱스 방침은 {@link CountryEntity}와 같다 — 초성 컬럼에만 두고, prod는 {@code text_pattern_ops}
 * opclass로 만든다({@code docs/ddl/cities.sql}).
 */
@Getter
@Entity
@Table(name = "cities", indexes = {
        @Index(name = "idx_cities_search_chosung", columnList = "search_chosung")
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
