package com.soma.yeolo.course.service;

import static com.soma.yeolo.global.client.JsonNodes.number;
import static com.soma.yeolo.global.client.JsonNodes.text;
import static com.soma.yeolo.global.client.JsonNodes.textList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.PlaceRegistry;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 코스의 방문지를 내부 장소로 정규화한다. (DOM-3 §"장소 정보 처리 기준")
 *
 * <p>AI는 stop마다 {@code place} 객체로 장소를 준다(API-AI-2 개정판: Google Place ID
 * {@code googlePlaceId}·이름·분류·좌표). 저장 직전에 각 장소를 내부 장소로 등록하고, AI 의 식별자 키는
 * 지운 뒤 {@code place.placeId}에 <b>내부 식별자(UUID)를 심는다</b>. 이 치환이 이 클래스의 핵심이다 —
 * 같은 장소를 코스마다 중복 저장하지 않고, 저장 JSON 이 특정 provider 식별자 형식에 묶이지 않게 한다.
 * 코스 상세 조회(API-COURSE-2)는 반대로 이 내부 식별자를 장소 테이블의 Google Place ID 로 되돌려
 * {@code googlePlaceId}로 내려준다({@code ItineraryGooglePlaceIdMapper}).
 *
 * <p><b>AI 식별자 키는 {@code googlePlaceId}다(API-AI-2, SPEC 0bd2d08 / Yeolo-AI 4066b4b, 2026-10-25).</b>
 * 개정 전 키 {@code placeId}로만 오면 경고를 남기고 같은 값으로 받는다 — 키 하나 때문에 검증된 식별자를
 * 버리면 장소가 식별자 없이 저장되는데(prod 에서 실제로 모든 장소가 그렇게 됐다, 2026-11-04), 그 실패가
 * 로그 한 줄 없이 조용했다. 경고는 AI 가 개정 전으로 되돌아간 것을 드러내기 위한 것이다.
 *
 * <p><b>BE 는 코스 생성에서 외부 지도 API 를 부르지 않는다(2026-11-04 결정).</b> AI 가 식별자·좌표를 빠뜨린
 * stop 은 예전처럼 장소명으로 provider(Google Text Search·OSM)를 조회하지 않고, <b>정규화하지 않은 채</b>
 * 남긴다(식별자 없음, 이름·좌표는 AI 값 그대로라 지도 표시는 된다). Google Maps API 는 취향 분석의
 * 역지오코딩에서만 쓴다 — 코스 생성의 Text Search 가 과금의 주범이었고(#121), API-AI-2 가 Google Place ID 를
 * 보장하므로 BE 가 메울 이유가 없다.
 *
 * <p><b>정규화 실패는 코스 생성을 실패시키지 않는다.</b> AI 생성(수십 초)이 이미 끝난 뒤의 부가
 * 단계이므로, 장소 한 곳을 못 등록했다고 코스 전체를 버리지 않는다. 대신 그 stop의
 * {@code place.placeId}를 <b>제거</b>해 정규화되지 않은 장소를 명확히 남긴다 — 저장 JSON 에는 장소
 * 테이블과 연결된 내부 식별자만 남긴다는 불변식을 지키기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPlaceNormalizer {

    /** AI 가 주는 Google Place ID 의 키 (API-AI-2). 응답 키(API-COURSE-2)와 같은 이름이며 명세가 함께 바꿨다. */
    static final String AI_PLACE_ID_KEY = ItineraryStorageJson.RESPONSE_PLACE_ID_KEY;
    /**
     * {@code placeId} — 한 키에 두 역할이 있다. <b>들어올 때</b>는 2026-10-25 개정 전 AI 의 식별자 키(값은
     * Google Place ID), <b>저장될 때</b>는 내부 장소 식별자(UUID) 키(DOM-3)다. 그래서 AI 값은 어느 키로
     * 왔든 먼저 지우고, 등록에 성공한 경우에만 같은 키에 내부 UUID 를 심는다. 지우는 단계를 빼면 등록에
     * 실패한 stop 에 AI 의 외부 식별자가 내부 식별자인 양 남는다.
     */
    static final String PLACE_ID_KEY = ItineraryStorageJson.STORED_PLACE_ID_KEY;

    private final PlaceRegistry placeRegistry;

    /** 코스 JSON의 모든 stop을 제자리에서 정규화한다. */
    public void normalize(JsonNode course) {
        // 한 코스 안에서 같은 장소가 여러 번 등장하는 일은 흔하다(숙소가 매일 첫/마지막 stop 등).
        // 코스 단위로 결과를 재사용해 같은 장소를 반복 등록하지 않는다.
        Map<String, SavedPlace> registered = new HashMap<>();
        int[] legacyKeyStops = {0};
        ItineraryStorageJson.forEachPlace(course.path("itinerary"), place -> {
            if (normalizePlace(place, registered)) {
                legacyKeyStops[0]++;
            }
        });
        if (legacyKeyStops[0] > 0) {
            // 코스당 한 번만 — stop 마다 남기면 코스 하나에 수십 줄이 찍혀 신호가 묻힌다.
            log.warn("AI 가 개정 전 키 '{}'로 장소 식별자를 보냈다(API-AI-2 는 '{}'). stops={}",
                    PLACE_ID_KEY, AI_PLACE_ID_KEY, legacyKeyStops[0]);
        }
    }

    /** @return AI 가 개정 전 키로만 식별자를 보냈는지 */
    private boolean normalizePlace(ObjectNode place, Map<String, SavedPlace> registered) {
        String placeName = text(place, "placeName");
        // 정규화에 성공할 때만 다시 채운다 — AI가 준 외부 식별자는 어느 키로 왔든 그대로 두지 않는다.
        String providerPlaceId = text(place, AI_PLACE_ID_KEY);
        // 실제로 식별자를 받았을 때만 '개정 전 키'로 센다 — 빈 값·잘못된 형태를 AI 의 되돌림으로 오진하지 않는다.
        String legacyId = text(place, PLACE_ID_KEY);
        boolean legacyKey = providerPlaceId == null && legacyId != null;
        if (legacyKey) {
            providerPlaceId = legacyId;
        }
        place.remove(AI_PLACE_ID_KEY);
        place.remove(PLACE_ID_KEY);

        if (placeName == null) {
            return legacyKey;
        }
        Place supplied = toSuppliedPlace(place, providerPlaceId, placeName);
        if (supplied == null) {
            log.info("AI 가 장소 식별자·좌표를 온전히 주지 않아 정규화하지 않고 남긴다(외부 조회 없음) - '{}'",
                    placeName);
            return legacyKey;
        }
        try {
            // 키는 Place 가 정규화한 식별자로 — AI 가 같은 장소를 places/ChIJ… 와 ChIJ… 로 섞어 보내도 한 번만 등록한다.
            SavedPlace saved = registered.computeIfAbsent(supplied.providerPlaceId(),
                    key -> placeRegistry.register(supplied));
            place.put(PLACE_ID_KEY, saved.placeId().toString());
            place.put("latitude", saved.latitude());
            place.put("longitude", saved.longitude());
        } catch (RuntimeException e) {
            // 장소 등록의 예기치 못한 실패. 이 장소만 미정규화로 남기고 코스 생성은 계속한다.
            log.warn("장소 정규화 중 오류 - '{}': {}", placeName, e.toString());
        }
        return legacyKey;
    }

    /**
     * AI가 준 {@code place}를 저장용 장소로 옮긴다. 식별자나 좌표가 없으면 저장할 수 없으므로
     * ({@link Place}의 불변식) null을 돌려준다.
     */
    private static Place toSuppliedPlace(ObjectNode place, String providerPlaceId, String placeName) {
        Double latitude = number(place, "latitude");
        Double longitude = number(place, "longitude");
        if (providerPlaceId == null || latitude == null || longitude == null) {
            return null;
        }
        // 명세 개정(2026-10)으로 AI 계약은 식별자·이름·분류·좌표뿐이다. 그 밖의 상세(영문명·주소·
        // 평점·사진·운영시간)는 전환기 AI가 덤으로 보낼 수 있으므로 오면 그대로 담고 없으면 null로 둔다.
        return new Place(
                providerPlaceId,
                placeName,
                text(place, "placeEngName"),
                text(place, "category"),
                text(place, "address"),
                latitude,
                longitude,
                number(place, "rating"),
                text(place, "photoUrl"),
                textList(place, "openingHours")
        );
    }
}
