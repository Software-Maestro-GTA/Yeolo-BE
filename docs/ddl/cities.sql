-- TSK-32 (#46) 도시 자동완성 (API-LOC-2) — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- countries 와 함께 만든다(docs/ddl/countries.sql). 데이터는 앱이 기동 시 적재한다.
CREATE TABLE cities (
    city_id         VARCHAR(32) NOT NULL,  -- GeoNames geonameid
    name_ko         TEXT        NOT NULL,  -- 표시용 도시명. 한국어 이름이 없으면 원문(라틴) 표기
    search_name     TEXT        NOT NULL,  -- 부분 일치 검색 키(소문자·구분자 제거)
    search_chosung  TEXT        NOT NULL,  -- 초성 검색 키
    country_id      VARCHAR(2)  NOT NULL,  -- countries.country_id
    country_name_ko TEXT        NOT NULL,  -- 응답용 비정규화 국가명(두 테이블은 함께 적재된다)
    population      BIGINT      NOT NULL,  -- 자동완성 정렬 기준
    PRIMARY KEY (city_id)
);

-- countries 에 대한 FK는 두지 않는다. 두 테이블은 데이터셋 교체 시 각각 delete+insert 되므로
-- FK가 있으면 적재 순서에 묶인다. 참조 무결성은 적재 시점에 보장한다(없는 국가의 도시는 버린다).

-- 인덱스 전략은 countries.sql 주석과 같다 — 초성만 인덱스를 두고, PostgreSQL에서 LIKE 'ㄷ%'가
-- 실제로 인덱스를 타도록 text_pattern_ops opclass로 만든다.
CREATE INDEX idx_cities_search_chosung ON cities (search_chosung text_pattern_ops);

-- search_name 은 부분 일치라 인덱스를 두지 않는다(순차 스캔). 3만 건대에서 실측 1~2ms 수준이며,
-- 자동완성 디바운스 안에 들어간다. 데이터가 더 커지면 pg_trgm GIN 인덱스를 검토한다.
