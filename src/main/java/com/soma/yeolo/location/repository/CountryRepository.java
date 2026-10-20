package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CountryEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 국가 조회 (API-LOC-1/2/3). 2026-10 개정으로 부분 일치·초성 검색은 없어졌고, 전체 목록·완전 일치·
 * 접두어 검색만 남았다.
 *
 * <p>검색 키({@code searchName}·{@code searchNameEn})는 적재 시 {@code SearchKeys.normalize}로 만든
 * 소문자·구분자 제거 문자열이라 영문 대소문자를 구분하지 않는다(명세의 요구). 이름 매칭은 <b>두 언어의
 * 키를 모두</b> 본다 — 검색 입력 언어와 응답 언어는 별개다(명세 §언어 정책). 정렬은 호출자가 응답
 * 언어의 표시명 속성({@code LocationLanguage.property()})으로 {@link Sort}를 넘긴다.
 */
public interface CountryRepository extends JpaRepository<CountryEntity, String> {

    /** 정규화된 이름으로 완전 일치, 두 언어 모두 (API-LOC-2 {@code countryName}, API-LOC-3 1단계, 사진·통화). */
    @Query("""
            select c from CountryEntity c
            where c.searchName = :key or c.searchNameEn = :key
            """)
    Optional<CountryEntity> findByAnySearchName(@Param("key") String key);

    /** 식별자 목록의 국가 — 검색 결과의 국가 순서를 DB 정렬 하나로 맞추기 위한 조회. */
    List<CountryEntity> findByCountryIdIn(Collection<String> countryIds, Sort sort);

    /** 정규화된 이름의 접두어 일치, 두 언어 모두 (API-LOC-3 2단계). {@code prefix}는 LIKE 패턴. */
    @Query("""
            select c from CountryEntity c
            where c.searchName like :prefix escape '!' or c.searchNameEn like :prefix escape '!'
            """)
    List<CountryEntity> searchByPrefix(@Param("prefix") String prefix, Sort sort);
}
