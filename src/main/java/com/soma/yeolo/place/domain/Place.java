package com.soma.yeolo.place.domain;

import java.util.List;

/**
 * 저장용 장소 도메인 (DOM-3 §"장소 정보 처리 기준"). AI가 반환한 장소명을 외부 provider로 정규화한
 * 결과를 앱이 쓸 수 있는 내부 장소 정보로 담는 순수 모델이다.
 *
 * <p>{@code providerPlaceId}는 provider(Google/OSM) 측 식별자로, <b>같은 장소를 중복 저장하지 않기
 * 위한 내부 키일 뿐 FE 응답에 절대 노출하지 않는다.</b> 앱에는 저장 시 부여되는 내부
 * {@code placeId}(UUID)만 내려간다. (DOM-3: "Google Place ID는 앱에 노출하지 않는다")
 *
 * <p>식별자·장소명·좌표는 필수다 — 이 중 하나라도 없는 조회 결과는 코스 지도 표시나 장소 상세에
 * 쓸 수 없으므로 장소로 저장하지 않는다(해당 stop은 정규화되지 않은 채로 남는다).
 *
 * @param providerPlaceId provider 측 장소 식별자 (내부 전용, FE 비노출)
 * @param placeName       장소명
 * @param category        장소 분류
 * @param address         주소 (provider가 주지 않으면 null)
 * @param latitude        위도
 * @param longitude       경도
 * @param rating          평점 (provider가 주지 않으면 null)
 * @param photoUrls       사진 URL 목록 (없으면 빈 목록)
 * @param openingHours    운영시간 문자열 목록 (없으면 빈 목록)
 */
public record Place(
        String providerPlaceId,
        String placeName,
        String category,
        String address,
        double latitude,
        double longitude,
        Double rating,
        List<String> photoUrls,
        List<String> openingHours
) {

    public Place {
        if (isBlank(providerPlaceId)) {
            throw new IllegalArgumentException("providerPlaceId is required");
        }
        if (isBlank(placeName)) {
            throw new IllegalArgumentException("placeName is required");
        }
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
        openingHours = openingHours == null ? List.of() : List.copyOf(openingHours);
    }

    /**
     * 분류를 {@code category}로 바꾼 사본. 값이 비어 있으면 provider 분류를 그대로 유지한다.
     *
     * <p>AI가 stop에 붙인 분류를 우선하기 위해 쓴다 — 코스 상세의 stop {@code category}와 장소 상세의
     * {@code category}가 어긋나 보이지 않게 한다.
     *
     * <p>단, 이는 <b>그 장소를 처음 등록할 때만</b> 반영된다. 이미 저장된 장소는 기존 행을 그대로
     * 재사용하므로, 나중에 다른 코스가 같은 장소에 다른 분류를 붙여도 장소 상세의 분류는 바뀌지 않는다.
     */
    public Place withCategory(String category) {
        if (isBlank(category)) {
            return this;
        }
        return new Place(providerPlaceId, placeName, category, address,
                latitude, longitude, rating, photoUrls, openingHours);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
