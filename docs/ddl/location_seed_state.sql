-- TSK-32 (#46) 기준 데이터셋 적재 상태 — dev/prod 배포 전 선적용 DDL
-- dev(ddl-auto=update)는 배포 시 Hibernate가 이 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 배포 전에 직접 적용해야 파드가 기동한다.
--
-- 데이터셋 파일의 #version 을 기록해 재적재 여부를 판단한다. 이 표가 없으면 부팅마다 국가·도시를
-- 통째로 다시 넣게 되고, 롤링 배포 중 구 파드가 서빙하는 테이블을 신 파드가 비우는 창이 생긴다.
CREATE TABLE IF NOT EXISTS location_seed_state (
    dataset VARCHAR(32) NOT NULL,  -- 'countries' | 'cities'
    version TEXT        NOT NULL,  -- 데이터셋 파일의 #version 값
    PRIMARY KEY (dataset)
);
