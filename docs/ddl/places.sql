-- TSK-38 (#48) 장소 상세 조회 — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
-- 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
CREATE TABLE places (
    id                UUID                     NOT NULL,
    provider_place_id VARCHAR(255)             NOT NULL UNIQUE,
    place_name        TEXT                     NOT NULL,
    category          VARCHAR(255),
    address           TEXT,
    latitude          DOUBLE PRECISION         NOT NULL,
    longitude         DOUBLE PRECISION         NOT NULL,
    rating            DOUBLE PRECISION,
    photo_urls        TEXT,
    opening_hours     TEXT,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);
