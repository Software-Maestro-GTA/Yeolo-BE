package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.UUID;

/**
 * 코스 상세의 {@code itinerary} 페이로드 (API-FB-7 / DOM-2 §4). FE가 타입으로 소비할 수 있도록
 * 명세의 일자·방문지 구조를 그대로 필드로 노출한다.
 *
 * <p>저장된 원본 itinerary JSON을 이 타입으로 역직렬화해 전달한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Itinerary(List<Day> days) {

    /** 일자별 일정. */
    public record Day(
            Integer day,
            String date,
            String memo,
            List<Stop> stops
    ) {
    }

    /**
     * 일자 내 방문지.
     *
     * <p>{@code placeId}·{@code latitude}·{@code longitude}는 AI 응답에 없고 BE의 장소 정규화
     * (DOM-3)로 채워진다. 장소를 찾지 못한 stop은 세 값이 모두 {@code null}이며, 이때 FE는 장소
     * 상세(API-PLACE-1)로 이동할 수 없다.
     *
     * <p>{@code placeId}를 {@code UUID}로 선언한 것은 계약이자 안전장치다 — 내부 식별자만이 이 타입을
     * 통과하므로, Google Place ID 같은 외부 식별자가 저장된 JSON에 섞여 있어도 FE 응답으로 나갈 수
     * 없다. 정규화 이전에 저장된 코스를 읽다 깨지지 않도록 UUID가 아닌 값은
     * {@link LenientUuidDeserializer}가 null로 떨어뜨린다.
     * (DOM-3: "Google Place ID는 앱에 노출하지 않는다")
     */
    public record Stop(
            Integer sequence,
            @JsonDeserialize(using = LenientUuidDeserializer.class) UUID placeId,
            String placeName,
            String category,
            Double latitude,
            Double longitude,
            String arrivalTime,
            Integer stayMinutes,
            String memo,
            String transportToNext,
            Integer travelMinutesToNext,
            Integer cost,
            String reason
    ) {
    }
}
