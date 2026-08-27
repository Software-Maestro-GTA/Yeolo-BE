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
 * 국가 기준 정보 (API-LOC-1). 코스 생성 화면의 지역 선택에 쓰이는 읽기 전용 참조 데이터로,
 * CLDR 한국어 지역명을 근거로 부팅 시 적재한다({@code LocationSeedLoader}).
 *
 * <p>도메인 명세(DOM)에 정의가 없는 참조 데이터이며, 보호할 불변식·상태 전이가 없어
 * "엔티티=도메인" 병합형으로 둔다(docs/architecture.md §1-1). 앱은 이 테이블을 읽기만 하고,
 * 값의 근거는 데이터셋 파일이다.
 *
 * <p>{@code search_name}·{@code search_chosung}은 표시용 이름에서 미리 계산한 검색 키다
 * ({@link com.soma.yeolo.location.domain.SearchKeys}). 조회 시점에 함수로 만들면 매 행마다 계산이
 * 돌므로 컬럼으로 들고 있는다.
 *
 * <p><b>인덱스는 초성 컬럼에만 둔다.</b> 이름 검색은 부분 일치({@code LIKE '%키워드%'})라 btree로는
 * 어차피 못 타서, 인덱스를 만들어 봐야 쓰기 비용만 늘고 조회는 그대로 순차 스캔이다. 초성 검색은
 * 앞부분 일치라 인덱스를 쓸 수 있는데, <b>PostgreSQL에서는 기본 collation 아래 plain btree가
 * {@code LIKE 'ㄷ%'}에 쓰이지 않는다</b> — prod DDL은 {@code text_pattern_ops}로 만든다
 * ({@code docs/ddl/countries.sql}). 여기 선언은 opclass를 표현할 수 없어 dev/H2용 평범한 btree다.
 */
@Getter
@Entity
@Table(name = "countries", indexes = {
        @Index(name = "idx_countries_search_chosung", columnList = "search_chosung")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CountryEntity {

    /** ISO 3166-1 alpha-2 국가 코드 (예: {@code JP}). 명세의 {@code countryId}. */
    @Id
    @Column(name = "country_id", length = 2, nullable = false, updatable = false)
    private String countryId;

    @Column(name = "name_ko", nullable = false, columnDefinition = "text")
    private String nameKo;

    @Column(name = "search_name", nullable = false, columnDefinition = "text")
    private String searchName;

    @Column(name = "search_chosung", nullable = false, columnDefinition = "text")
    private String searchChosung;

    /**
     * 데이터셋 한 행 → 엔티티. 검색 키 파생을 여기 한 곳으로 모은다 — 적재기가 각자 계산하면
     * 국가와 도시의 규칙이 갈라질 수 있고, 그 어긋남은 "검색이 가끔 안 된다"로만 드러난다.
     */
    public static CountryEntity of(String countryId, String nameKo) {
        CountryEntity entity = new CountryEntity();
        entity.countryId = countryId;
        entity.nameKo = nameKo;
        entity.searchName = SearchKeys.normalize(nameKo);
        entity.searchChosung = SearchKeys.chosung(nameKo);
        return entity;
    }
}
