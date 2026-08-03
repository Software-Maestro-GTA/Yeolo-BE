package com.soma.yeolo.course.client;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * AI가 반환한 장소명을 BE에서 정규화한 결과 (DOM-3 장소 정보 처리 기준). 앱에 노출하는 <b>내부</b>
 * {@code placeId}와 좌표(위도/경도)를 담는다. Google Place ID 등 외부 식별자는 노출하지 않는다.
 *
 * <p>좌표는 지오코딩 실패·미해결 시 {@code null}일 수 있으나, {@code placeId}는 장소명 기준으로 항상
 * 결정적으로 부여해 같은 장소가 같은 내부 식별자로 연결되도록 한다.
 *
 * @param placeId   내부 장소 식별자 (항상 부여)
 * @param latitude  위도 (미해결 시 {@code null})
 * @param longitude 경도 (미해결 시 {@code null})
 */
public record NormalizedPlace(String placeId, Double latitude, Double longitude) {

    /**
     * 국가·도시·장소명으로 결정적 내부 {@code placeId}를 부여해 정규화 결과를 만든다. 같은 (국가,
     * 도시, 장소명)은 항상 동일한 {@code placeId}로 매핑된다(이름 기반 UUID, 무작위성 없음).
     */
    public static NormalizedPlace of(String country, String city, String placeName, Double latitude,
                                     Double longitude) {
        String seed = normalize(country) + "|" + normalize(city) + "|" + normalize(placeName);
        String placeId = "place_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return new NormalizedPlace(placeId, latitude, longitude);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
