package com.soma.yeolo.place.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 저장용 장소 도메인 (DOM-3 §"장소 정보 처리 기준"). AI가 stop 마다 준 장소(API-AI-2)를 앱이 쓸 수
 * 있는 내부 장소 정보로 담는 순수 모델이다.
 *
 * <p>{@code providerPlaceId}는 provider(Google. 폴백 시절 행은 OSM/stub) 측 식별자로, 같은 장소를 중복 저장하지 않기 위한
 * 키다. 저장 시 부여되는 내부 {@code placeId}(UUID)가 코스 stop에 심긴다. 코스 상세 조회(API-COURSE-2)는
 * 그 내부 식별자를 <b>Google Place ID로 되돌려</b> 내려보낸다(2026-10-25 결정, FE가 Google Maps 를 Place
 * ID 로 연다). Google 형식이 아닌 식별자({@code osm:}·{@code stub:})는 FE에 노출하지 않는다 —
 * {@link #toGooglePlaceId(String)}.
 *
 * <p>식별자·장소명·좌표는 필수다 — 이 중 하나라도 없는 장소는 코스 지도 표시에 쓸 수 없으므로
 * 저장하지 않는다(해당 stop은 정규화되지 않은 채로 남는다. BE 가 외부 조회로 메우지 않는다).
 *
 * @param providerPlaceId provider 측 장소 식별자 (중복 저장 방지 키. Google 형식이면 코스 상세에서 Google Place ID 로 노출)
 * @param placeName       장소명
 * @param placeEngName    장소 영문명 (없으면 null)
 * @param category        장소 분류
 * @param address         주소 (provider가 주지 않으면 null)
 * @param latitude        위도
 * @param longitude       경도
 * @param rating          평점 (provider가 주지 않으면 null)
 * @param photoUrl        대표 사진 URL (없으면 null) — 명세 개정으로 목록에서 단일 값이 됐다
 * @param openingHours    운영시간 문자열 목록 (없으면 빈 목록)
 */
public record Place(
        String providerPlaceId,
        String placeName,
        String placeEngName,
        String category,
        String address,
        double latitude,
        double longitude,
        Double rating,
        String photoUrl,
        List<String> openingHours
) {

    public Place {
        if (isBlank(providerPlaceId)) {
            throw new IllegalArgumentException("providerPlaceId is required");
        }
        // Places API (New) 리소스 이름(places/ChIJ…)으로 들어온 식별자는 저장 전에 접두사를 벗긴다. 중복 저장
        // 방지 키(provider_place_id unique)가 같은 장소를 두 형태로 두 번 저장하지 않게 하기 위해서다.
        providerPlaceId = stripGoogleResourcePrefix(providerPlaceId);
        if (isBlank(placeName)) {
            throw new IllegalArgumentException("placeName is required");
        }
        openingHours = openingHours == null ? List.of() : List.copyOf(openingHours);
    }

    /**
     * provider 식별자를 FE 가 Google Maps 에 넘길 수 있는 Google Place ID 로 바꾼다. Google 형식이 아니면
     * {@code null}.
     *
     * <p>Google Place ID 는 영숫자·{@code -}·{@code _}로만 이뤄진다({@code ChIJ…}·{@code Ei…} 등). Places API
     * (New)의 리소스 이름 {@code places/ChIJ…} 형태는 생성자가 저장 전에 벗기지만, 그 전에 저장된 행을 위해
     * 여기서도 벗긴다 — AI 는 벗겨서 보내기로 돼 있어도(DOM-5) 전환기 빌드가 남긴 값이 있을 수 있다. BE 가
     * 합성하는 provider 식별자는 {@code osm:}·{@code stub:}처럼 이름공간 접두사를 붙이므로 콜론이 들어가 걸러진다.
     *
     * <p><b>전제:</b> 접두사 없는 식별자는 Google 것이다 — 현재 접두사 없이 등록되는 경로는 AI(Google)와
     * (지금은 AI 뿐이다 — 이전의 provider 조회 폴백 {@code GooglePlaceLookupClient}는 2026-11 에 제거됐다). 콜론 없는 식별자를 쓰는 provider 를 새로 붙이면 그 어댑터가
     * 이름공간 접두사를 붙여야 한다(또는 provider 를 컬럼으로 기록하는 쪽으로 바꿔야 한다).
     */
    public static String toGooglePlaceId(String providerPlaceId) {
        if (providerPlaceId == null) {
            return null;
        }
        String id = stripGoogleResourcePrefix(providerPlaceId);
        return GOOGLE_PLACE_ID.matcher(id).matches() ? id : null;
    }

    private static String stripGoogleResourcePrefix(String providerPlaceId) {
        return providerPlaceId.startsWith(GOOGLE_RESOURCE_PREFIX)
                ? providerPlaceId.substring(GOOGLE_RESOURCE_PREFIX.length()) : providerPlaceId;
    }

    private static final String GOOGLE_RESOURCE_PREFIX = "places/";
    private static final Pattern GOOGLE_PLACE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
