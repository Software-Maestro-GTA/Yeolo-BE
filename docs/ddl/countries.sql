-- TSK-32 (#46) 국가 자동완성 (API-LOC-1) — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- 데이터는 넣지 않는다. 앱이 기동 시 저장소의 데이터셋 파일(src/main/resources/data/locations/)을
-- 읽어 적재한다 — 상세는 docs/location-dataset.md.
CREATE TABLE countries (
    country_id     VARCHAR(2) NOT NULL,
    name_ko        TEXT       NOT NULL,  -- 표시용 국가명(CLDR 한국어 지역명)
    search_name    TEXT       NOT NULL,  -- 부분 일치 검색 키(소문자·구분자 제거)
    search_chosung TEXT       NOT NULL,  -- 초성 검색 키(예: 대한민국 → ㄷㅎㅁㄱ)
    PRIMARY KEY (country_id)
);

-- 초성 검색은 앞부분 일치(LIKE 'ㄷ%')라 인덱스를 쓸 수 있다. 단 PostgreSQL은 DB collation이
-- C/POSIX가 아니면(RDS 기본값은 en_US.UTF-8) plain btree를 LIKE 'x%'에 쓰지 않는다 —
-- text_pattern_ops opclass가 있어야 탄다. 엔티티의 @Index 선언은 opclass를 표현할 수 없어
-- dev(ddl-auto)에는 평범한 btree가 생기지만, validate는 인덱스 정의를 검사하지 않으므로 무방하다.
CREATE INDEX idx_countries_search_chosung ON countries (search_chosung text_pattern_ops);

-- search_name 에는 인덱스를 두지 않는다. 이름 검색은 부분 일치(LIKE '%키워드%')라 btree로는
-- 어차피 못 타서 쓰기 비용만 늘고 조회는 그대로 순차 스캔이다. 행이 250개 남짓이라 문제되지 않는다.
-- 데이터가 크게 늘어 병목이 되면 pg_trgm GIN 인덱스를 검토한다(CREATE EXTENSION 권한 필요).
