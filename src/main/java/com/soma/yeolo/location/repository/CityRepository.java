package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CityEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 도시 조회 (API-LOC-2/3, 도시 사진 폴백). 2026-10 개정으로 부분 일치·초성 검색은 없어졌다.
 * 이름 매칭은 한국어·영문 검색 키를 모두 보고, 정렬은 호출자가 응답 언어의 표시명 속성으로 넘긴다
 * ({@code CountryRepository} 설명 참고).
 */
public interface CityRepository extends JpaRepository<CityEntity, String> {

    /** 한 국가의 전체 도시 — 페이지네이션 없음(API-LOC-2 명세). 정렬은 표시명 오름차순으로 넘긴다. */
    List<CityEntity> findByCountryId(String countryId, Sort sort);

    /** 여러 국가의 전체 도시를 한 번에 (API-LOC-3). 국가별로 묶이도록 {@code countryId} 를 앞세워 정렬한다. */
    List<CityEntity> findByCountryIdIn(Collection<String> countryIds, Sort sort);

    /** 정규화된 이름의 접두어 일치, 두 언어 모두, 전 세계 대상 (API-LOC-3). 그룹화는 서비스가 한다. */
    @Query("""
            select c from CityEntity c
            where c.searchName like :prefix escape '!' or c.searchNameEn like :prefix escape '!'
            """)
    List<CityEntity> searchByPrefix(@Param("prefix") String prefix, Sort sort);

    /**
     * 국가 안에서 정규화된 이름으로 완전 일치(두 언어 모두), 인구순. 같은 나라에 동명 도시가 있으면
     * (미국의 Springfield 등) 인구가 많은 쪽이 앞이다 — 호출자는 첫 행만 쓴다.
     */
    @Query("""
            select c from CityEntity c
            where c.countryId = :countryId and (c.searchName = :key or c.searchNameEn = :key)
            order by c.population desc
            """)
    List<CityEntity> findByCountryIdAndAnySearchName(@Param("countryId") String countryId,
                                                     @Param("key") String key, Pageable pageable);

    /**
     * 국가 안에서 정규화된 이름의 접두어 일치(두 언어 모두), 인구순. 데이터셋은 행정 접미사를 뗀
     * 이름("부산")을 들고 있지만 AI·사용자 입력이 짧거나 길 수 있어 완전 일치 다음 단계로 쓴다(사진 조회).
     * {@code prefix}는 LIKE 패턴이다.
     */
    @Query("""
            select c from CityEntity c
            where c.countryId = :countryId
              and (c.searchName like :prefix escape '!' or c.searchNameEn like :prefix escape '!')
            order by c.population desc
            """)
    List<CityEntity> findByCountryIdAndAnySearchNamePrefix(@Param("countryId") String countryId,
                                                           @Param("prefix") String prefix, Pageable pageable);

    /** 한 국가에서 사진이 있는 도시를 인구순으로 (사진 폴백의 마지막 단계). */
    @Query("""
            select c from CityEntity c
            where c.countryId = :countryId
              and exists (select 1 from CityImageEntity i where i.cityId = c.cityId)
            order by c.population desc
            """)
    List<CityEntity> findWithImageByCountryIdOrderByPopulationDesc(@Param("countryId") String countryId,
                                                                   Pageable pageable);
}
