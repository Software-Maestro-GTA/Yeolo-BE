package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CountryEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 국가 조회 (API-LOC-1/2/3). 2026-10 개정으로 부분 일치·초성 검색은 없어졌고, 전체 목록·완전 일치·
 * 접두어 검색만 남았다. 검색 키({@code searchName})는 적재 시 {@code SearchKeys.normalize}로 만든
 * 소문자·구분자 제거 문자열이라 영문 대소문자를 구분하지 않는다(명세의 요구).
 */
public interface CountryRepository extends JpaRepository<CountryEntity, String> {

    /** 전체 국가를 표시명 오름차순으로 (API-LOC-1). */
    List<CountryEntity> findAllByOrderByNameKoAsc();

    /** 정규화된 이름으로 완전 일치 (API-LOC-2 {@code countryName}, API-LOC-3 1단계). */
    Optional<CountryEntity> findBySearchName(String searchName);

    /** 식별자 목록의 국가를 표시명 오름차순으로 — 검색 결과의 국가 순서를 DB 정렬 하나로 맞추기 위한 조회. */
    List<CountryEntity> findByCountryIdInOrderByNameKoAsc(Collection<String> countryIds);

    /** 정규화된 이름의 접두어 일치, 표시명 오름차순 (API-LOC-3 2단계). {@code prefix}는 LIKE 패턴. */
    @Query("""
            select c from CountryEntity c
            where c.searchName like :prefix escape '!'
            order by c.nameKo
            """)
    List<CountryEntity> searchByPrefix(@Param("prefix") String prefix);
}
