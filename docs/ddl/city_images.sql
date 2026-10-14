-- 도시 대표 사진 (API-PREF-4 recommendCity.imageUrl, API-COURSE-3 coverImageUrl 폴백) — prod 배포 전 선적용 DDL
--
-- dev(ddl-auto=update)는 배포 시 Hibernate가 테이블을 자동 생성하므로 수동 적용이 필요 없다.
-- prod(ddl-auto=validate)는 자동 생성하지 않으므로, 이 파일을 배포 전에 직접 적용해야 파드가 기동한다.
-- (docs/architecture.md §2) 접근: SSM으로 bastion 경유 (Yeolo-Infra docs/dev-environment.md 참고)
--
-- 저장소의 src/main/resources/data/locations/city_images.tsv 를 기동 시 적재하는 파생 테이블이다
-- (docs/location-dataset.md). 행은 앱이 통째로 교체하므로 데이터 마이그레이션은 없다.
-- 사진 파일 자체는 scripts/build-location-images.py 가 Pixabay 에서 받아 S3 에 올린다 — 여기에는
-- 환경에 무관한 S3 키만 있고 URL 은 location.images.base-url 과 합쳐 런타임에 만든다.

CREATE TABLE IF NOT EXISTS city_images (
    city_id   VARCHAR(32) NOT NULL,  -- cities.city_id (GeoNames geonameid)
    s3_key    TEXT        NOT NULL,  -- 버킷 안의 객체 키 (locations/cities/<cityId>.jpg)
    source    VARCHAR(16) NOT NULL,  -- 'pixabay' | 'manual' — 라이선스 추적용, API 로 내보내지 않는다
    source_id TEXT        NOT NULL,  -- 출처 쪽 사진 식별자 (Pixabay 이미지 id 등)
    PRIMARY KEY (city_id)
);
