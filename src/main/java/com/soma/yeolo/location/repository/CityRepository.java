package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CityEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 도시 조회 (API-LOC-2/3, 도시 사진 폴백). 2026-10 개정으로 부분 일치·초성 검색은 없어졌다.
 * 검색 키({@code searchName})는 소문자·구분자 제거 문자열이라 영문 대소문자를 구분하지 않는다.
 */
public interface CityRepository extends JpaRepository<CityEntity, String> {

    /** 한 국가의 전체 도시를 표시명 오름차순으로 — 페이지네이션 없음(API-LOC-2 명세). */
    List<CityEntity> findByCountryIdOrderByNameKoAsc(String countryId);

    /** 여러 국가의 전체 도시를 한 번에 — 국가별로 묶이고 국가 안에서는 표시명 오름차순이다 (API-LOC-3). */
    List<CityEntity> findByCountryIdInOrderByCountryIdAscNameKoAsc(Collection<String> countryIds);

    /** 정규화된 이름의 접두어 일치, 전 세계 대상 (API-LOC-3). 그룹화는 서비스가 한다. */
    @Query("""
            select c from CityEntity c
            where c.searchName like :prefix escape '!'
            order by c.countryId, c.nameKo
            """)
    List<CityEntity> searchByPrefix(@Param("prefix") String prefix);

    /**
     * 국가 안에서 정규화된 이름으로 완전 일치. 같은 나라에 동명 도시가 있으면(미국의 Springfield 등)
     * 인구가 많은 쪽을 고른다.
     */
    Optional<CityEntity> findFirstByCountryIdAndSearchNameOrderByPopulationDesc(String countryId,
                                                                               String searchName);

    /**
     * 국가 안에서 정규화된 이름의 접두어 일치, 인구순. GeoNames 한국어명은 행정 접미사를 달고 있어
     * ("부산광역시"·"서울특별시") AI·사용자가 주는 "부산"으로는 완전 일치가 안 된다 — 사진 조회의 2단계.
     * {@code prefix}는 정규화된 키(영숫자·한글만)라 LIKE 와일드카드가 섞이지 않는다.
     */
    Optional<CityEntity> findFirstByCountryIdAndSearchNameStartingWithOrderByPopulationDesc(String countryId,
                                                                                           String prefix);

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
