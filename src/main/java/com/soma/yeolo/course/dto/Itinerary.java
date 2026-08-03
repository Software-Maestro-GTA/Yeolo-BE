package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

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

    /** 일자 내 방문지. {@code placeId}·{@code latitude}·{@code longitude}는 BE 장소 정규화 결과이며,
     * 미해결 시 {@code null}로 응답에서 생략된다(NON_NULL). (DOM-3 장소 정보 처리 기준) */
    public record Stop(
            Integer sequence,
            String placeId,
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
