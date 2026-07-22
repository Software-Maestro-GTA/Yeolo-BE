package com.soma.yeolo.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.Course;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CourseAssembler assembler = new CourseAssembler();

    private JsonNode courseNode() throws Exception {
        return MAPPER.readTree("""
                {
                  "title": "2박 3일 제주 힐링 코스",
                  "destinationCountry": "대한민국",
                  "destinationCity": "제주",
                  "region": "서귀포",
                  "startDate": "2026-08-01",
                  "totalDays": 3,
                  "totalCost": 480000,
                  "tags": ["힐링", "카페"],
                  "recommendationReason": "여유로운 일정",
                  "itinerary": {"days": [{"day": 1, "stops": []}]}
                }
                """);
    }

    @Test
    void 승격_필드를_추출하고_itinerary는_원본_JSON으로_보존한다() throws Exception {
        UUID userId = UUID.randomUUID();

        Course course = assembler.toDomain(userId, courseNode());

        assertThat(course.userId()).isEqualTo(userId);
        assertThat(course.title()).isEqualTo("2박 3일 제주 힐링 코스");
        assertThat(course.destinationCountry()).isEqualTo("대한민국");
        assertThat(course.destinationCity()).isEqualTo("제주");
        assertThat(course.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(course.totalDays()).isEqualTo(3);
        assertThat(course.totalCost()).isEqualTo(480000);
        assertThat(course.tags()).containsExactly("힐링", "카페");
        assertThat(course.recommendationReason()).isEqualTo("여유로운 일정");
        assertThat(MAPPER.readTree(course.itineraryJson()).get("days")).hasSize(1);
    }

    @Test
    void 필수값이_없으면_AI_생성_오류로_노출한다() throws Exception {
        JsonNode noTitle = MAPPER.readTree("""
                {"destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"totalCost":1,"itinerary":{"days":[]}}
                """);

        assertThatThrownBy(() -> assembler.toDomain(UUID.randomUUID(), noTitle))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_COURSE_GENERATION_ERROR);
    }

    @Test
    void itinerary가_없으면_AI_생성_오류로_노출한다() throws Exception {
        JsonNode noItinerary = MAPPER.readTree("""
                {"title":"t","destinationCountry":"대한민국","destinationCity":"제주",
                 "startDate":"2026-08-01","totalDays":3,"totalCost":1}
                """);

        assertThatThrownBy(() -> assembler.toDomain(UUID.randomUUID(), noItinerary))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_COURSE_GENERATION_ERROR);
    }
}
