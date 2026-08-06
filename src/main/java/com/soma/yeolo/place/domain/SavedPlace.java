package com.soma.yeolo.place.domain;

import java.util.List;
import java.util.UUID;

/**
 * 저장된 장소 조회 결과 (API-PLACE-1). 신규 저장용 {@link Place}와 달리 부여된 내부 식별자
 * {@code placeId}를 함께 담는 읽기 전용 모델이다.
 *
 * <p>이 모델에는 provider 측 식별자를 담지 않는다 — 조회 응답 조립 과정에서 Google Place ID 등
 * 외부 식별자가 새어 나갈 여지를 타입 수준에서 없앤다. (DOM-3)
 *
 * @param placeId      내부 장소 식별자 (코스 stop의 {@code placeId})
 * @param placeName    장소명
 * @param category     장소 분류
 * @param address      주소 (없으면 null)
 * @param latitude     위도
 * @param longitude    경도
 * @param rating       평점 (없으면 null)
 * @param photoUrls    사진 URL 목록 (없으면 빈 목록)
 * @param openingHours 운영시간 문자열 목록 (없으면 빈 목록)
 */
public record SavedPlace(
        UUID placeId,
        String placeName,
        String category,
        String address,
        double latitude,
        double longitude,
        Double rating,
        List<String> photoUrls,
        List<String> openingHours
) {

    public SavedPlace {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
        openingHours = openingHours == null ? List.of() : List.copyOf(openingHours);
    }
}
