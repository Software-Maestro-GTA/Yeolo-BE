package com.soma.yeolo.location.repository;

import com.soma.yeolo.location.entity.CountryEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 국가 리포지토리 (API-LOC-1). 병합형 참조 도메인이라 별도 포트 없이 서비스가 직접 쓴다
 * (docs/architecture.md §1-2 "병합형 예외").
 *
 * <p>패턴 파라미터는 {@link com.soma.yeolo.location.domain.LikePatterns}가 만든 값이어야 한다 —
 * 와일드카드 이스케이프와 쿼리의 {@code ESCAPE '!'}가 짝을 이룬다.
 */
public interface CountryRepository extends JpaRepository<CountryEntity, String> {

    /**
     * 이름 부분 일치 검색. 앞부분이 일치하는 후보를 먼저 올린다 — "국"으로 검색했을 때
     * "한국"보다 "국가명이 국으로 시작하는 나라"가 위에 오는 편이 자동완성으로서 자연스럽다.
     *
     * @param contains {@code %kw%} 패턴
     * @param prefix   {@code kw%} 패턴 (정렬용, {@code contains}와 같은 검색어에서 나와야 한다)
     */
    @Query("""
            select c from CountryEntity c
            where c.searchName like :contains escape '!'
            order by case when c.searchName like :prefix escape '!' then 0 else 1 end,
                     length(c.nameKo), c.nameKo
            """)
    List<CountryEntity> searchByName(@Param("contains") String contains,
                                     @Param("prefix") String prefix,
                                     Pageable pageable);

    /**
     * 초성 검색. 초성은 앞에서부터 입력하는 것이므로 앞부분 일치만 본다
     * ({@code ㄷ} → 대한민국·덴마크, {@code ㅁㄱ} → 미국).
     *
     * @param prefix {@code kw%} 패턴
     */
    @Query("""
            select c from CountryEntity c
            where c.searchChosung like :prefix escape '!'
            order by length(c.nameKo), c.nameKo
            """)
    List<CountryEntity> searchByChosung(@Param("prefix") String prefix, Pageable pageable);
}
