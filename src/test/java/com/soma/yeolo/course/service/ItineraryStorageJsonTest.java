package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.dto.Itinerary.Day;
import com.soma.yeolo.course.dto.Itinerary.Place;
import com.soma.yeolo.course.dto.Itinerary.Stop;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장소 식별자 필드의 JSON 계약. 응답은 {@code googlePlaceId}(API-COURSE-2, SPEC 0bd2d08), 저장 JSON 은
 * 내부 UUID 를 {@code placeId} 키로 든다(DOM-3) — 같은 필드가 두 이름을 오가므로 양방향을 모두 고정한다.
 * 저장 JSON 은 {@code CourseQueryService}가 실제로 쓰는 {@link ItineraryStorageJson}으로 읽는다.
 */
class ItineraryStorageJsonTest {

    private static final String GOOGLE_PLACE_ID = "ChIJN1t_tDeuEmsRUsoyG83frY4";

    private static Itinerary itineraryWith(Place place) {
        return new Itinerary(List.of(new Day(1, "2026-08-01",
                List.of(new Stop(1, 60, null, null, null, place, null)))));
    }

    /**
     * 명세 개정(2026-10-25)으로 응답 필드명이 {@code placeId} → {@code googlePlaceId}로 바뀌었다. 옛 이름은
     * 응답에 나가면 안 된다. Spring MVC 응답은 Jackson 3 이 직렬화하므로 그쪽으로도 확인한다.
     */
    @Test
    void 응답은_googlePlaceId로_나가고_개정_전_placeId는_나가지_않는다() throws Exception {
        Itinerary itinerary = itineraryWith(new Place(GOOGLE_PLACE_ID, "성산일출봉", "nature", 33.4581, 126.9425));

        String jackson2 = new ObjectMapper().writeValueAsString(itinerary);
        String jackson3 = JsonMapper.builder().build().writeValueAsString(itinerary);

        for (String json : List.of(jackson2, jackson3)) {
            assertThat(json).contains("\"googlePlaceId\":\"" + GOOGLE_PLACE_ID + "\"");
            assertThat(json).doesNotContain("\"placeId\"");
        }
    }

    /** 저장 JSON 의 내부 식별자 키는 {@code placeId}다 — 저장 키를 바꾸지 않아 기존 코스가 그대로 읽힌다. */
    @Test
    void 저장_JSON의_placeId_키를_내부_식별자로_읽는다() throws Exception {
        UUID internal = UUID.randomUUID();

        Itinerary stored = ItineraryStorageJson.read(
                "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,\"place\":{\"placeId\":\"" + internal
                        + "\",\"placeName\":\"성산일출봉\"}}]}]}");

        Place place = stored.days().get(0).stops().get(0).place();
        assertThat(place.googlePlaceId()).isEqualTo(internal.toString());
        assertThat(place.placeName()).isEqualTo("성산일출봉");
    }

    /**
     * 전환기 코스(Yeolo-AI 4066b4b 배포 2026-10-25 ~ 이 수정 전)의 실제 저장 형태: 개정 전 정규화기가 지우지 못한
     * AI 의 Google Place ID {@code googlePlaceId}와, 이름 조회 폴백이 덧붙인 내부 {@code placeId}(UUID)가
     * <b>함께</b> 있고 장소 필드도 모두 차 있다. 두 키를 한 필드의 별칭으로 읽으면 Jackson 이 중복 생성자 인자로
     * 실패해 상세 조회가 500 이 됐다(리뷰 재현). 키 순서와 무관하게 읽혀야 하고, 폴백 UUID 는 동명의 다른 장소일
     * 수 있으므로 AI 가 검증한 Google Place ID 를 우선한다 — 그래야 전환기 코스가 데이터 수선 없이 맞는 핀을 연다.
     */
    @Test
    void 전환기_저장_JSON은_AI의_googlePlaceId를_우선해_읽는다() throws Exception {
        UUID internal = UUID.randomUUID();
        String aiFirst = "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,\"place\":{"
                + "\"googlePlaceId\":\"ChIJ_RAW_FROM_AI\",\"placeName\":\"성산일출봉\",\"category\":\"nature\","
                + "\"latitude\":33.4581,\"longitude\":126.9425,\"placeId\":\"" + internal + "\"}}]}]}";
        String internalFirst = "{\"days\":[{\"day\":1,\"stops\":[{\"sequence\":1,\"place\":{"
                + "\"placeId\":\"" + internal + "\",\"placeName\":\"성산일출봉\",\"category\":\"nature\","
                + "\"latitude\":33.4581,\"longitude\":126.9425,\"googlePlaceId\":\"ChIJ_RAW_FROM_AI\"}}]}]}";

        for (String json : List.of(aiFirst, internalFirst)) {
            Place place = ItineraryStorageJson.read(json).days().get(0).stops().get(0).place();

            assertThat(place.googlePlaceId()).as(json).isEqualTo("ChIJ_RAW_FROM_AI");
            assertThat(place.placeName()).isEqualTo("성산일출봉");
            assertThat(place.category()).isEqualTo("nature");
            assertThat(place.latitude()).isEqualTo(33.4581);
            assertThat(place.longitude()).isEqualTo(126.9425);
        }
    }

    /**
     * 장소 테이블과 연결되지 않는 값은 버린다 — 정규화(DOM-3) 전 코스의 {@code placeId}에 남은 외부 식별자,
     * Google 형식이 아닌 {@code googlePlaceId}, 객체·배열 같은 잘못된 형태. 이름·좌표는 남는다.
     */
    @Test
    void 저장_JSON의_연결되지_않는_식별자는_버리고_나머지_필드는_읽는다() throws Exception {
        List<String> placeJsons = List.of(
                "{\"placeId\":\"ChIJ_RAW\",\"placeName\":\"성산일출봉\"}",
                "{\"googlePlaceId\":\"osm:node123\",\"placeName\":\"성산일출봉\"}",
                "{\"googlePlaceId\":{\"id\":\"ChIJ_RAW\"},\"placeId\":[\"x\"],\"placeName\":\"성산일출봉\"}",
                "{\"googlePlaceId\":\"\",\"placeId\":null,\"placeName\":\"성산일출봉\"}");

        for (String placeJson : placeJsons) {
            Itinerary stored = ItineraryStorageJson.read(
                    "{\"days\":[{\"day\":1,\"stops\":[{\"place\":" + placeJson + "}]}]}");

            Place place = stored.days().get(0).stops().get(0).place();
            assertThat(place.googlePlaceId()).as(placeJson).isNull();
            assertThat(place.placeName()).as(placeJson).isEqualTo("성산일출봉");
        }
    }
}
