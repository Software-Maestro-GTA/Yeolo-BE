-- TSK-32 (#46) 기준 데이터셋 적재 상태 — dev/prod 배포 전 선적용 DDL
-- dev·prod는 ddl-auto=validate이므로, 이 테이블이 없으면 파드 기동이 실패한다.
--
-- 데이터셋 파일의 #version 을 기록해 재적재 여부를 판단한다. 이 표가 없으면 부팅마다 국가·도시를
-- 통째로 다시 넣게 되고, 롤링 배포 중 구 파드가 서빙하는 테이블을 신 파드가 비우는 창이 생긴다.
CREATE TABLE location_seed_state (
    dataset VARCHAR(32) NOT NULL,  -- 'countries' | 'cities'
    version TEXT        NOT NULL,  -- 데이터셋 파일의 #version 값
    PRIMARY KEY (dataset)
);
