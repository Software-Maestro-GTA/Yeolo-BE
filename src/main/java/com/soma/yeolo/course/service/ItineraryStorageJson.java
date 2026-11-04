package com.soma.yeolo.course.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.place.domain.Place;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 저장된 코스 itinerary JSON 을 응답 타입 {@link Itinerary}로 읽는다. 저장 JSON 을 읽는 <b>유일한</b> 경로다.
 *
 * <p>저장 JSON 과 응답은 장소 식별자의 <b>키와 뜻이 다르다.</b> 저장 JSON 은 {@code ItineraryPlaceNormalizer}가
 * 심은 BE 내부 식별자(UUID)를 {@value #STORED_PLACE_ID_KEY} 키로 들고(DOM-3 "내부 placeId"), 응답은
 * {@value #RESPONSE_PLACE_ID_KEY}에 Google Place ID 를 싣는다(API-COURSE-2, SPEC 0bd2d08). 저장 키를 바꾸지
 * 않아 기존 코스에 마이그레이션이 없다.
 *
 * <p>stop 의 {@code place} 식별자는 아래 규칙으로 {@code googlePlaceId} 한 자리에 모아 읽는다. 트리를 먼저
 * 손보고 바인딩하므로 {@link Itinerary}는 저장 키·검증 규칙을 모른다.
 * <ol>
 *   <li><b>저장 JSON 에 Google 형식의 {@code googlePlaceId}가 있으면 그 값.</b> 전환기 코스(Yeolo-AI 4066b4b
 *       배포 2026-10-25 ~ BE 가 키를 맞춘 수정 전)에만 있다 — 개정 전 정규화기는 {@code placeId} 키만 지워서
 *       AI 가 검증해 보낸 Google Place ID 가 그대로 남았고, 이름 조회 폴백이 {@code placeId}(UUID)를 덧붙였다.
 *       그 UUID 는 동명의 <b>다른</b> 장소일 수 있으므로 AI 값을 우선한다. 이 값은 장소 테이블을 거치지 않고
 *       그대로 응답에 나간다({@code ItineraryGooglePlaceIdMapper}는 UUID 만 되돌린다). 그래서 전환기 코스는
 *       데이터 수선 없이 올바른 장소의 Google Maps 링크를 연다. <b>단, 좌표는 복구하지 못한다</b> — 개정 전
 *       정규화기가 폴백 장소의 좌표로 덮어썼고 AI 의 원래 좌표는 저장 JSON 에 남아 있지 않다. 폴백이 동명의 다른
 *       장소를 골랐다면 마커 위치(좌표)와 링크(식별자)가 서로 다른 장소를 가리킬 수 있다. 바로잡으려면 오프라인
 *       데이터 수선이 필요하다(BE 는 코스 생성·조회에서 지도 API 를 부르지 않는다).</li>
 *   <li>아니면 {@code placeId}가 UUID 정규형이면 그 값(내부 식별자). 매퍼가 장소 테이블의 Google Place ID 로
 *       되돌린다.</li>
 *   <li>그 밖의 값(장소 정규화(DOM-3) 도입 전 코스에 남은 외부 식별자, 객체·배열 같은 잘못된 형태, Google 형식이
 *       아닌 {@code googlePlaceId})은 버린다 — 장소 테이블과 연결되지 않고 FE 가 Google 에 넘겨도 의미가 없다.
 *       버려도 이름·좌표는 남아 지도 표시는 된다.</li>
 * </ol>
 *
 * <p>두 키를 한 필드의 Jackson 별칭으로 읽는 방법은 쓰지 않는다 — 전환기 코스처럼 두 키가 함께 있으면 중복
 * 생성자 인자로 역직렬화가 실패해 상세 조회가 500 이 된다(리뷰에서 재현).
 */
public final class ItineraryStorageJson {

    /** 저장 JSON 의 내부 장소 식별자 키 (DOM-3). */
    public static final String STORED_PLACE_ID_KEY = "placeId";
    /**
     * 응답의 장소 식별자 키 (API-COURSE-2). AI 가 보내는 키(API-AI-2)와 같은 이름이다 — SPEC 0bd2d08 이 둘을
     * 함께 바꿨으므로 {@code ItineraryPlaceNormalizer}도 이 상수를 쓴다.
     */
    public static final String RESPONSE_PLACE_ID_KEY = "googlePlaceId";

    // 저장된 원본 itinerary JSON에 명세에 없는 필드가 섞일 수 있으므로 미지 필드는 무시하고 역직렬화한다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ItineraryStorageJson() {
    }

    /**
     * @throws JsonProcessingException 저장 JSON 이 손상된 경우
     */
    public static Itinerary read(String itineraryJson) throws JsonProcessingException {
        JsonNode root = OBJECT_MAPPER.readTree(itineraryJson);
        forEachPlace(root, ItineraryStorageJson::liftPlaceId);
        return OBJECT_MAPPER.treeToValue(root, Itinerary.class);
    }

    /**
     * itinerary 트리({@code days[].stops[].place})의 모든 장소 객체를 순회한다. 저장 JSON 을 읽는 쪽과
     * AI 코스를 저장 전에 정규화하는 쪽이 같은 구조를 걷으므로 한 곳에 둔다 — 구조가 바뀌면 둘 다 따라온다.
     *
     * @param itinerary {@code days}를 가진 노드 (저장 JSON 의 루트, 또는 AI 코스의 {@code itinerary})
     */
    public static void forEachPlace(JsonNode itinerary, Consumer<ObjectNode> action) {
        for (JsonNode day : itinerary.path("days")) {
            for (JsonNode stop : day.path("stops")) {
                if (stop.path("place") instanceof ObjectNode place) {
                    action.accept(place);
                }
            }
        }
    }

    private static void liftPlaceId(ObjectNode place) {
        String storedGoogleId = googlePlaceId(place.remove(RESPONSE_PLACE_ID_KEY));
        String internalId = internalPlaceId(place.remove(STORED_PLACE_ID_KEY));
        String lifted = storedGoogleId != null ? storedGoogleId : internalId;
        if (lifted != null) {
            place.put(RESPONSE_PLACE_ID_KEY, lifted);
        }
    }

    /** Google 형식의 문자열만 통과시킨다. */
    private static String googlePlaceId(JsonNode value) {
        return value != null && value.isTextual() ? Place.toGooglePlaceId(value.asText()) : null;
    }

    /** UUID 정규형 문자열만 통과시킨다. */
    private static String internalPlaceId(JsonNode value) {
        UUID id = value == null || !value.isTextual() ? null : internalPlaceId(value.asText());
        return id == null ? null : id.toString();
    }

    /**
     * 읽어 올린 {@code googlePlaceId} 값이 내부 식별자(UUID)면 그 UUID, 저장된 Google Place ID 면 {@code null}.
     * {@code ItineraryGooglePlaceIdMapper}가 "UUID 만 되돌리고 나머지는 그대로 둔다"를 판정할 때 이 규칙을 공유한다.
     */
    static UUID internalPlaceId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
