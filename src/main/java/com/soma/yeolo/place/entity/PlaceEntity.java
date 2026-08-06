package com.soma.yeolo.place.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.global.entity.StringListJsonConverter;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 정보 (DOM-3 §"장소 정보 처리 기준" / API-PLACE-1). AI가 반환한 장소명을 provider로 정규화한
 * 결과를 저장해, 코스 stop이 내부 {@code placeId}로 참조하고 장소 상세 조회의 근거가 되게 한다.
 *
 * <p>{@code provider_place_id}에 unique 제약을 둬 같은 장소가 코스마다 중복 저장되지 않게 한다
 * (외부 조회 결과 캐시 역할). 이 컬럼은 <b>내부 전용</b>이며 {@link #toSavedPlace()}가 만드는 읽기
 * 모델에는 담기지 않는다 — Google Place ID가 FE로 새어 나갈 경로를 차단한다.
 */
@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider_place_id", nullable = false, unique = true)
    private String providerPlaceId;

    @Column(name = "place_name", nullable = false, columnDefinition = "text")
    private String placeName;

    @Column(name = "category")
    private String category;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "rating")
    private Double rating;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "photo_urls", columnDefinition = "text")
    private List<String> photoUrls;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "opening_hours", columnDefinition = "text")
    private List<String> openingHours;

    @Builder
    private PlaceEntity(String providerPlaceId, String placeName, String category, String address,
                        double latitude, double longitude, Double rating,
                        List<String> photoUrls, List<String> openingHours) {
        this.providerPlaceId = providerPlaceId;
        this.placeName = placeName;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
        this.photoUrls = photoUrls;
        this.openingHours = openingHours;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static PlaceEntity from(Place place) {
        return PlaceEntity.builder()
                .providerPlaceId(place.providerPlaceId())
                .placeName(place.placeName())
                .category(place.category())
                .address(place.address())
                .latitude(place.latitude())
                .longitude(place.longitude())
                .rating(place.rating())
                .photoUrls(place.photoUrls())
                .openingHours(place.openingHours())
                .build();
    }

    /** 영속 엔티티 → 조회용 읽기 모델 매핑 (API-PLACE-1). provider 식별자는 담지 않는다. */
    public SavedPlace toSavedPlace() {
        return new SavedPlace(id, placeName, category, address, latitude, longitude,
                rating, photoUrls, openingHours);
    }
}
