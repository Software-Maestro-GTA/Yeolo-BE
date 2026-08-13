-- TSK-38 (#48) 장소 상세 조회 — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
CREATE TABLE places (
    id                UUID                     NOT NULL,
    provider_place_id VARCHAR(255)             NOT NULL UNIQUE,
    place_name        TEXT                     NOT NULL,
    place_eng_name    TEXT,
    category          VARCHAR(255),
    address           TEXT,
    latitude          DOUBLE PRECISION         NOT NULL,
    longitude         DOUBLE PRECISION         NOT NULL,
    rating            DOUBLE PRECISION,
    photo_url         TEXT,
    opening_hours     TEXT,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- 명세 개정(API-PLACE-1: placeEngName 추가, photoUrls[] → photoUrl) 반영분.
-- 위 CREATE 문은 새로 만드는 환경용으로 이미 갱신돼 있다. **이미 이 테이블을 만든 환경**
-- (dev/prod)에는 아래를 적용한다.
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 photo_url·place_eng_name 컬럼을 자동 추가하지만,
-- 쓰지 않게 된 photo_urls 는 지우지 않는다. prod(validate)는 컬럼을 추가하지도 않으므로
-- 이 파일을 배포 전에 직접 적용해야 파드가 기동한다. (docs/architecture.md §2)
--
-- 데이터 이관은 하지 않는다. photo_urls 는 실제로 늘 빈 목록이었다 — Google Places의 사진 URL은
-- API 키를 쿼리 파라미터로 요구해 그대로 내보낼 수 없어 어댑터가 담지 않았고, OSM·스텁도
-- 사진을 주지 않는다(GooglePlaceLookupClient 문서 참고). 즉 옮길 값이 없다.
-- 앞으로 photo_url 은 AI가 코스와 함께 준 사진 URL로 채워진다(API-AI-2 → ItineraryPlaceNormalizer).
ALTER TABLE places
    ADD COLUMN IF NOT EXISTS place_eng_name TEXT,
    ADD COLUMN IF NOT EXISTS photo_url      TEXT;

-- photo_urls 제거는 **애플리케이션 배포가 끝난 뒤** 별도로 실행한다. 배포 전에 지우면 아직
-- 옛 엔티티를 들고 있는 파드가 없는 컬럼을 조회해 500이 난다.
-- ALTER TABLE places DROP COLUMN IF EXISTS photo_urls;
