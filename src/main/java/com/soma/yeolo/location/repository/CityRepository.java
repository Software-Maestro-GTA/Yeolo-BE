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
 * 그 안에서는 인구가 많은 순으로 올린다 — 명세는 "전체 국가 대상 도시 후보"만 요구하므로
 * 국가로 좁히지 않고 전 세계에서 찾되, 사용자가 떠올릴 법한 도시가 위로 오게 한다.
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
}
