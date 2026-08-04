package com.soma.yeolo.course.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.dto.CourseListResponse.CourseSummary;
import com.soma.yeolo.course.service.CourseQueryService;
import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class CourseQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseQueryService courseQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 코스_목록_조회는_명세_메시지와_요약_필드로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        when(courseQueryService.getMyCourses(userId)).thenReturn(new CourseListResponse(List.of(
                new CourseSummary(courseId.toString(), "제주 힐링 여행", "대한민국", "제주",
                        LocalDate.of(2026, 8, 1), 3, List.of("힐링", "자연"), "추천 이유",
                        Instant.parse("2026-07-01T00:00:00Z")))));

        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("여행 코스 목록 조회 성공"))
                .andExpect(jsonPath("$.data.courses[0].courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.data.courses[0].title").value("제주 힐링 여행"))
                .andExpect(jsonPath("$.data.courses[0].destinationCountry").value("대한민국"))
                .andExpect(jsonPath("$.data.courses[0].destinationCity").value("제주"))
                .andExpect(jsonPath("$.data.courses[0].startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.courses[0].totalDays").value(3))
                .andExpect(jsonPath("$.data.courses[0].tags").value(Matchers.contains("힐링", "자연")))
                .andExpect(jsonPath("$.data.courses[0].recommendationReason").value("추천 이유"))
                .andExpect(jsonPath("$.data.courses[0].createdAt").exists());
    }

    @Test
    void 미인증_목록_조회는_401로_응답한다() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 코스_삭제_성공시_200과_명세_봉투로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("여행 코스 삭제 성공"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(courseQueryService).deleteCourse(userId, courseId);
    }

    @Test
    void 없는_코스_삭제는_404와_명세_메시지로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        doThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND))
                .when(courseQueryService).deleteCourse(eq(userId), eq(courseId));

        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("여행 코스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 타인_코스_삭제는_403과_명세_메시지로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        doThrow(new BusinessException(ErrorCode.COURSE_DELETE_ACCESS_DENIED))
                .when(courseQueryService).deleteCourse(eq(userId), eq(courseId));

        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("해당 여행 코스를 삭제할 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 미인증_코스_삭제는_401로_응답한다() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
