-- TSK-32 (#46) 국가 자동완성 (API-LOC-1) — dev/prod 배포 전 선적용 DDL
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 배포 전에 직접 적용해야 파드가 기동한다.
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- 데이터는 넣지 않는다. 앱이 기동 시 저장소의 데이터셋 파일(src/main/resources/data/locations/)을
-- 읽어 적재한다 — 상세는 docs/location-dataset.md.
CREATE TABLE IF NOT EXISTS countries (
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
-- 앱이 먼저 부팅한 환경에는 ddl-auto가 같은 이름의 평범한 btree를 이미 만들어 두었고,
-- IF NOT EXISTS는 이름으로만 대조하므로 그대로면 opclass 인덱스 생성이 조용히 무시된다.
-- 그래서 먼저 지운다(새 환경에서는 no-op).
DROP INDEX IF EXISTS idx_countries_search_chosung;
CREATE INDEX IF NOT EXISTS idx_countries_search_chosung ON countries (search_chosung text_pattern_ops);

-- search_name 에는 인덱스를 두지 않는다. 이름 검색은 부분 일치(LIKE '%키워드%')라 btree로는
-- 어차피 못 타서 쓰기 비용만 늘고 조회는 그대로 순차 스캔이다. 행이 250개 남짓이라 문제되지 않는다.
-- 데이터가 크게 늘어 병목이 되면 pg_trgm GIN 인덱스를 검토한다(CREATE EXTENSION 권한 필요).

-- [2026-10] 명세 개정(#97) — 수도·통화. 수도는 추천 도시 사진 폴백(LocationImageService)에, 통화 기호는
-- 코스 상세의 exRateIcon(#100)에 쓴다. 값이 없는 영토(남극 등)가 있어 모두 NULL 허용이다.
-- dev 는 ddl-auto=update 가 추가하고, prod 는 이 문장이 추가한다. 적재 INSERT 가 이 컬럼을 쓰므로
-- 새 이미지 배포 '전'에 적용해야 한다.
ALTER TABLE countries
    ADD COLUMN IF NOT EXISTS capital_city_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS currency_code   VARCHAR(3),
    ADD COLUMN IF NOT EXISTS currency_symbol TEXT;

-- [2026-10] 접두어·완전 일치 검색(API-LOC-2/3)이 search_name 을 쓰므로 인덱스를 둔다.
-- dev 는 엔티티 @Index 로 ddl-auto 가 만들고, prod 는 이 문장이 만든다(text_pattern_ops 로 LIKE 'x%' 대응).
CREATE INDEX IF NOT EXISTS idx_countries_search_name ON countries (search_name text_pattern_ops);

-- [2026-10] search_chosung 은 초성 검색이 명세에서 빠져 더 이상 조회에 쓰지 않는다. 다만 NOT NULL 이고
-- dev 의 ddl-auto=update 는 컬럼을 지우지 않아 적재 INSERT 가 계속 값을 채운다. 지우려면 엔티티 매핑을
-- 먼저 걷어낸 뒤 배포 후 단계에서 아래를 실행한다(지금은 주석).
-- ALTER TABLE countries DROP COLUMN IF EXISTS search_chosung;

-- [2026-10] 언어별 표시명(한국어·영어, API-LOC-1/2/3 §언어 정책). 영문 표시명과 그 검색 키를 둔다.
-- NOT NULL 을 걸지 않는다 — 기존 행이 있는 테이블에 NOT NULL 컬럼을 더하면 dev 의 ddl-auto=update 가
-- 실패하고 적재까지 막힌다. 값은 적재가 항상 채우며, 앱은 비어 있으면 한국어명으로 보인다.
-- 적재 INSERT 가 이 컬럼을 쓰므로 새 이미지 배포 '전'에 적용해야 한다(수도·통화 컬럼과 같은 순서).
ALTER TABLE countries
    ADD COLUMN IF NOT EXISTS name_en        TEXT,
    ADD COLUMN IF NOT EXISTS search_name_en TEXT;
-- 완전 일치·접두어 검색이 두 키를 OR 로 보므로 영문 키에도 같은 인덱스를 둔다.
CREATE INDEX IF NOT EXISTS idx_countries_search_name_en ON countries (search_name_en text_pattern_ops);
