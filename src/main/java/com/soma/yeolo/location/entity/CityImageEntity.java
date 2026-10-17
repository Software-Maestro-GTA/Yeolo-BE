package com.soma.yeolo.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도시 대표 사진 (API-PREF-4 {@code recommendCity.imageUrl}, API-COURSE-3 {@code coverImageUrl} 폴백).
 * 저장소의 {@code city_images.tsv}를 기동 시 적재한 파생물이다 — 사진 파일은 빌드 스크립트
 * ({@code scripts/build-location-images.py})가 Pixabay 에서 받아 S3 에 올리고, 여기에는 환경에 무관한
 * S3 키만 둔다. URL 은 {@code location.images.base-url} 과 합쳐 런타임에 만든다.
 *
 * <p>출처는 라이선스 추적용으로만 보관하고 API 로 내보내지 않는다(Pixabay Content License 는 저작자
 * 표시가 필요 없다 — 저작자 표시가 필요한 출처는 섞지 않는다는 전제다. docs/spec-index.md).
 */
@Getter
@Entity
@Table(name = "city_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CityImageEntity {

    /** {@code cities.city_id} (GeoNames geonameid). */
    @Id
    @Column(name = "city_id", length = 32, nullable = false, updatable = false)
    private String cityId;

    /** 버킷 안의 객체 키. 예: {@code locations/cities/1850147.jpg}. */
    @Column(name = "s3_key", nullable = false, columnDefinition = "text")
    private String s3Key;

    /** 사진 출처 식별자. {@code pixabay} 또는 수동 지정 {@code manual}. */
    @Column(name = "source", length = 16, nullable = false)
    private String source;

    /** 출처 쪽 사진 식별자(Pixabay 이미지 id 등). 재검토·교체 시 추적용. */
    @Column(name = "source_id", nullable = false, columnDefinition = "text")
    private String sourceId;

    public static CityImageEntity of(String cityId, String s3Key, String source, String sourceId) {
        CityImageEntity entity = new CityImageEntity();
        entity.cityId = cityId;
        entity.s3Key = s3Key;
        entity.source = source;
        entity.sourceId = sourceId;
        return entity;
    }
}
