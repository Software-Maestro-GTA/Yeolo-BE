package com.soma.yeolo.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest.TripConditionPayload;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 코스 생성 스텁 구현(기본값). 여행 조건에 맞춰 형식이 완결된 {@code course} JSON을 합성해 반환한다.
 *
 * <p>{@code ai.course.provider=internal}로 설정하면 실제 {@code /internal/ai/courses}(API-BA-1) 연동
 * 어댑터(TSK-7 #4)가 대신 활성화된다. 스텁은 설정이 없거나 {@code ai.course.provider=stub}일 때
 * 사용되며, AI 서버 없이도 요청 검증 → 성향 로딩 → 진행 이벤트 → 저장 → complete 흐름을 검증하게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.course.provider", havingValue = "stub", matchIfMissing = true)
public class StubAiCourseClient implements AiCourseClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public JsonNode generateCourse(AiCourseGenerationRequest request) {
        TripConditionPayload trip = request.tripCondition();
        log.debug("AI course generation (stub) for {} {} ({}일)",
                trip.destinationCountry(), trip.destinationCity(), trip.totalDays());
        return buildCourse(trip);
    }

    private JsonNode buildCourse(TripConditionPayload trip) {
        LocalDate startDate = LocalDate.parse(trip.startDate());
        ObjectNode course = OBJECT_MAPPER.createObjectNode();
        course.put("title", "%d일 %s 추천 코스".formatted(trip.totalDays(), trip.destinationCity()));
        course.put("destinationCountry", trip.destinationCountry());
        course.put("destinationCity", trip.destinationCity());
        course.put("startDate", trip.startDate());
        course.put("totalDays", trip.totalDays());

        ArrayNode tags = course.putArray("tags");
        tags.add("추천").add(trip.budgetType());

        course.put("recommendationReason",
                "%s 예산 성향과 여행 조건에 맞춰 구성한 코스입니다.".formatted(trip.budgetType()));

        course.set("itinerary", buildItinerary(startDate, trip.totalDays()));
        return course;
    }

    private ObjectNode buildItinerary(LocalDate startDate, int totalDays) {
        ObjectNode itinerary = OBJECT_MAPPER.createObjectNode();
        ArrayNode days = itinerary.putArray("days");
        for (int i = 0; i < Math.max(totalDays, 1); i++) {
            ObjectNode day = days.addObject();
            day.put("day", i + 1);
            day.put("date", startDate.plusDays(i).toString());
            day.put("memo", "");

            ObjectNode stop = day.putArray("stops").addObject();
            stop.put("sequence", 1);
            stop.put("placeId", "");
            stop.put("placeName", "추천 장소");
            stop.put("category", "관광지");
            stop.put("arrivalTime", "10:00");
            stop.put("stayMinutes", 90);
            stop.put("memo", "");
            stop.put("transportToNext", "none");
            stop.put("travelMinutesToNext", 0);
            stop.put("cost", 0);
            stop.put("reason", "샘플 추천 사유");
        }
        return itinerary;
    }
}
