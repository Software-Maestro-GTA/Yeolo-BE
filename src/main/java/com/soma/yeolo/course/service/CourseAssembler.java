package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * AI가 산출한 {@code course} JSON을 저장용 {@link Course} 도메인으로 조립한다. 목록·상세에 쓰이는
 * 상위 정보는 승격 추출하고, 일자·방문지 전체({@code itinerary})는 원본 JSON으로 보존한다. (DOM-2)
 *
 * <p>필수 값 누락·형식 오류 등 AI 응답이 저장 가능한 형태가 아니면
 * {@code AI_COURSE_GENERATION_ERROR}(500)로 노출한다.
 */
@Component
public class CourseAssembler {

    /** AI 코스 노드를 소유자에 연결된 코스 도메인으로 변환한다. */
    public Course toDomain(UUID userId, JsonNode course) {
        if (course == null || !course.isObject()) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
        }
        return new Course(
                userId,
                requireText(course, "title"),
                requireText(course, "destinationCountry"),
                requireText(course, "destinationCity"),
                parseDate(requireText(course, "startDate")),
                intValue(course, "totalDays"),
                intValue(course, "totalCost"),
                stringList(course, "tags"),
                text(course, "recommendationReason"),
                itineraryJson(course)
        );
    }

    private String requireText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
        }
        return value.asInt();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR, e);
        }
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        array.forEach(item -> result.add(item.asText()));
        return result;
    }

    /** {@code itinerary} 서브트리를 원본 JSON 문자열로 보존한다(없으면 빈 객체). */
    private String itineraryJson(JsonNode course) {
        JsonNode itinerary = course.get("itinerary");
        if (itinerary == null || itinerary.isNull()) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
        }
        return itinerary.toString();
    }
}
