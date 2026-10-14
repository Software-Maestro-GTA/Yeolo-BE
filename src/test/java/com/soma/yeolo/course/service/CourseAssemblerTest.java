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
                  "startDate": "2026-08-01",
                  "totalDays": 3,
                  "totalCost": 350000,
                  "tags": ["힐링", "카페"],
                  "recommendationReason": "명세 개정으로 빠진 필드 — 무시되어야 한다",
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
        assertThat(course.tags()).containsExactly("힐링", "카페");
        assertThat(course.totalCost()).isEqualTo(350_000L);
        assertThat(course.coverImageUrl()).isNull();
        assertThat(MAPPER.readTree(course.itineraryJson()).get("days")).hasSize(1);
    }

    /** 총 비용은 계획용 예상치(DOM-5)라 AI가 못 줘도 코스는 성립한다 — 개정 전 AI 응답 호환. */
    @Test
    void totalCost가_없으면_null로_두고_코스는_조립한다() throws Exception {
        JsonNode noCost = MAPPER.readTree("""
                {"title":"t","destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"itinerary":{"days":[]}}
                """);

        assertThat(assembler.toDomain(UUID.randomUUID(), noCost).totalCost()).isNull();
    }

    /** 숫자 문자열은 받고, 숫자로 읽을 수 없는 값은 비용만 비우고 코스는 조립한다. */
    @Test
    void totalCost가_숫자_문자열이면_받고_아니면_null로_둔다() throws Exception {
        JsonNode textual = MAPPER.readTree("""
                {"title":"t","destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"totalCost":"350000","itinerary":{"days":[]}}
                """);
        JsonNode garbage = MAPPER.readTree("""
                {"title":"t","destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"totalCost":"약 35만원","itinerary":{"days":[]}}
                """);

        assertThat(assembler.toDomain(UUID.randomUUID(), textual).totalCost()).isEqualTo(350_000L);
        assertThat(assembler.toDomain(UUID.randomUUID(), garbage).totalCost()).isNull();
    }

    /** 전환기에 AI가 아직 대표 이미지를 주면(DOM-5) 목록 응답(API-COURSE-3)용으로 담아 둔다. */
    @Test
    void AI가_coverImageUrl을_주면_그대로_담는다() throws Exception {
        JsonNode withCover = MAPPER.readTree("""
                {"title":"t","destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"coverImageUrl":"https://cdn.example.com/cover.jpg","itinerary":{"days":[]}}
                """);

        assertThat(assembler.toDomain(UUID.randomUUID(), withCover).coverImageUrl())
                .isEqualTo("https://cdn.example.com/cover.jpg");
    }

    @Test
    void 필수값이_없으면_AI_생성_오류로_노출한다() throws Exception {
        JsonNode noTitle = MAPPER.readTree("""
                {"destinationCountry":"대한민국","destinationCity":"제주","startDate":"2026-08-01",
                 "totalDays":3,"itinerary":{"days":[]}}
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
                 "startDate":"2026-08-01","totalDays":3}
                """);

        assertThatThrownBy(() -> assembler.toDomain(UUID.randomUUID(), noItinerary))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_COURSE_GENERATION_ERROR);
    }
}
