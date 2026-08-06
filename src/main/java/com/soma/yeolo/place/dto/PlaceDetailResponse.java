package com.soma.yeolo.place.dto;

import com.soma.yeolo.place.domain.SavedPlace;
import java.util.List;

/**
 * 장소 상세 조회 응답의 {@code data} 페이로드 (API-PLACE-1). 필드명·구조는 명세 그대로 사용한다.
 *
 * <p>{@code placeId}는 <b>내부 장소 식별자</b>이며 Google Place ID 등 provider 식별자는 담지 않는다.
 * (DOM-3: "Google Place ID는 앱에 노출하지 않는다")
 */
public record PlaceDetailResponse(PlaceDetail place) {

    /**
     * 장소 상세.
     *
     * @param placeId      내부 장소 식별자 (코스 stop의 {@code placeId})
     * @param placeName    장소명
     * @param category     장소 분류
     * @param address      주소 — provider가 주지 않으면 {@code null}
     * @param latitude     위도
     * @param longitude    경도
     * @param rating       평점 — 없으면 {@code null}
     * @param photoUrls    사진 URL 목록 — 없으면 빈 배열
     * @param openingHours 운영시간 목록 — 없으면 빈 배열
     */
    public record PlaceDetail(
            String placeId,
            String placeName,
            String category,
            String address,
            double latitude,
            double longitude,
            Double rating,
            List<String> photoUrls,
            List<String> openingHours
    ) {
    }

    /** 저장된 장소 읽기 모델로 상세 응답을 조립한다. */
    public static PlaceDetailResponse from(SavedPlace place) {
        return new PlaceDetailResponse(new PlaceDetail(
                place.placeId().toString(),
                place.placeName(),
                place.category(),
                place.address(),
                place.latitude(),
                place.longitude(),
                place.rating(),
                place.photoUrls(),
                place.openingHours()
        ));
    }
}
