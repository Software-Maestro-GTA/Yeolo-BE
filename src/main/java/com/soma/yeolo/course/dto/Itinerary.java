package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * 코스 상세의 {@code itinerary} 페이로드 (API-COURSE-2 / DOM-3). FE가 타입으로 소비할 수 있도록
 * 명세의 일자·방문지 구조를 그대로 필드로 노출한다.
 *
 * <p>저장된 원본 itinerary JSON을 이 타입으로 역직렬화해 전달한다. 저장된 JSON에 명세 밖 필드가
 * 섞여 있어도(개정 전 코스의 day {@code memo}·stop {@code arrivalTime}, AI가 덤으로 준 사진 출처 등)
 * <b>여기 선언한 필드만 FE로 나간다</b>. 명세 개정(2026-10)으로 day {@code memo}와 stop
 * {@code arrivalTime}은 빠졌다. {@code transportToNext.cost}는 AI 계약에서는 빠졌지만 FE 계약에
 * 남아 있어 유지한다(새 코스는 null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Itinerary(List<Day> days) {

    /** 일자별 일정. */
    public record Day(
            Integer day,
            String date,
            List<Stop> stops
    ) {
    }

    /**
     * 일자 내 방문지.
     *
     * <p>명세 개정으로 장소 정보는 {@link Place}, 다음 방문지까지의 이동은 {@link TransportToNext}로
     * 묶였다(예전에는 stop에 평평하게 붙어 있었다).
     *
     * @param cost 이 방문지에서 쓰는 비용(입장료 등). {@link TransportToNext#cost()}(이동 비용)와는
     *             별개다 — 명세 개정으로 추가됐으므로 그 전에 저장된 코스에는 없고, 그때는 null이다.
     */
    public record Stop(
            Integer sequence,
            Integer stayMinutes,
            String memo,
            String reason,
            Integer cost,
            Place place,
            @JsonDeserialize(using = LenientTransportDeserializer.class) TransportToNext transportToNext
    ) {
    }

    /**
     * 방문 장소.
     *
     * <p>{@code placeId}는 <b>읽는 단계와 응답 단계에서 뜻이 다르다.</b> 저장된 itinerary JSON에는
     * {@code ItineraryPlaceNormalizer}가 저장 직전에 심은 BE 내부 식별자(UUID)가 들어 있고, 이 타입은
     * 그 JSON을 읽을 때 {@link LenientUuidDeserializer}로 내부 UUID 문자열만 통과시킨다(UUID가 아닌
     * 값은 null — 정규화 전 코스에 남은 외부 식별자가 그대로 새지 않게 하는 안전장치). 응답으로 나가기
     * 전에 {@code ItineraryGooglePlaceIdMapper}가 그 UUID를 장소 테이블의 <b>Google Place ID</b>로
     * 바꿔 넣는다 — FE가 이 값으로 Google Maps 를 연다(2026-10-25 결정. DOM-3 "Google Place ID
     * 비노출" 문구와 상충하므로 명세 개정이 뒤따라야 한다).
     *
     * <p>정규화하지 못한 장소, 장소 행이 없는 경우, Google 형식이 아닌 provider({@code osm:}·
     * {@code stub:})로 등록된 장소는 {@code placeId}가 {@code null}이다. 이름·좌표는 남아 있어 지도
     * 표시는 된다.
     */
    public record Place(
            @JsonDeserialize(using = LenientUuidDeserializer.class) String placeId,
            String placeName,
            String category,
            Double latitude,
            Double longitude
    ) {
    }

    /**
     * 다음 방문지까지의 이동.
     *
     * @param type     이동 수단 — {@code walking | transit | driving | taxi | none}
     * @param distance 이동 거리 (AI가 주지 않으면 null)
     * @param minutes  소요 시간(분) (AI가 주지 않으면 null)
     * @param cost     이동 비용 (AI가 주지 않으면 null)
     * @param memo     이동 관련 메모 (없으면 null)
     */
    public record TransportToNext(
            String type,
            Double distance,
            Integer minutes,
            Integer cost,
            String memo
    ) {
    }
}
