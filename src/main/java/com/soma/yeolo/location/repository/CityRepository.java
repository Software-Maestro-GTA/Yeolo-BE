package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CityEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 도시 리포지토리 (API-LOC-2). 국가와 마찬가지로 병합형 참조 도메인이다.
 *
 * <p>도시는 동명이 흔해서(스프링필드·산호세…) 정렬이 결과 품질을 좌우한다. 앞부분 일치를 먼저,
 * 그 안에서는 인구가 많은 순으로 올린다 — 사용자가 떠올릴 법한 도시가 위로 오게 한다.
 *
 * <p>{@code country}(명세 개정으로 추가된 선택 파라미터)를 주면 그 국가로 좁히고, 주지 않으면
 * 예전처럼 전 세계에서 찾는다. 국가 조건이 붙는 질의를 따로 두는 이유는 {@code :country is null}
 * 같은 조건절을 JPQL에 넣으면 파라미터 타입 추론이 DB 방언에 좌우되기 때문이다 — 갈래가 둘뿐이라
 * 질의를 나누는 편이 단순하다.
 */
public interface CityRepository extends JpaRepository<CityEntity, String> {

    /**
     * 이름 부분 일치 검색.
     *
     * @param contains {@code %kw%} 패턴
     * @param prefix   {@code kw%} 패턴 (정렬용)
     */
    @Query("""
            select c from CityEntity c
            where c.searchName like :contains escape '!'
            order by case when c.searchName like :prefix escape '!' then 0 else 1 end,
                     c.population desc, c.nameKo
            """)
    List<CityEntity> searchByName(@Param("contains") String contains,
                                  @Param("prefix") String prefix,
                                  Pageable pageable);

    /**
     * 초성 검색 (앞부분 일치).
     *
     * @param prefix {@code kw%} 패턴
     */
    @Query("""
            select c from CityEntity c
            where c.searchChosung like :prefix escape '!'
            order by c.population desc, c.nameKo
            """)
    List<CityEntity> searchByChosung(@Param("prefix") String prefix, Pageable pageable);

    /**
     * 국가로 좁힌 이름 부분 일치 검색.
     *
     * @param country ISO 3166-1 alpha-2 코드(대문자) 또는 국가 한국어명 — 근거는
     *                {@code LocationAutocompleteService} 문서 참고
     */
    @Query("""
            select c from CityEntity c
            where c.searchName like :contains escape '!'
              and (c.countryId = :country or c.countryNameKo = :countryName)
            order by case when c.searchName like :prefix escape '!' then 0 else 1 end,
                     c.population desc, c.nameKo
            """)
    List<CityEntity> searchByNameInCountry(@Param("contains") String contains,
                                           @Param("prefix") String prefix,
                                           @Param("country") String country,
                                           @Param("countryName") String countryName,
                                           Pageable pageable);

    /** 국가로 좁힌 초성 검색 (앞부분 일치). */
    @Query("""
            select c from CityEntity c
            where c.searchChosung like :prefix escape '!'
              and (c.countryId = :country or c.countryNameKo = :countryName)
            order by c.population desc, c.nameKo
            """)
    List<CityEntity> searchByChosungInCountry(@Param("prefix") String prefix,
                                              @Param("country") String country,
                                              @Param("countryName") String countryName,
                                              Pageable pageable);
}
