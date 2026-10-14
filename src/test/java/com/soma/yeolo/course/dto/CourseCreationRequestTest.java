package com.soma.yeolo.course.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 코스 생성 요청의 경계 검증 (API-COURSE-1, 2026-10 개정).
 *
 * <p>명세에서 제거된 값은 BE 경계에서 끊어야 실패가 AI 400까지 밀리지 않는다(CLAUDE.md §코드 컨벤션 —
 * {@code budgetType} 리버트를 테스트가 없어 놓친 전례). 옛 {@code budgetType}은 Spring Boot 기본
 * 역직렬화(미지 필드 무시)에서 조용히 버려지고 {@code budget}이 비어 400이 되는 것을 고정한다.
 */
class CourseCreationRequestTest {

    /** Spring Boot가 쓰는 설정과 같게 미지 필드를 무시한다. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violatedFields(CourseCreationRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void 금액_예산이_있으면_통과한다() {
        CourseCreationRequest request =
                new CourseCreationRequest("일본", "오사카", "2026-09-01", 3, BigDecimal.valueOf(500_000));

        assertThat(violatedFields(request)).isEmpty();
    }

    /** 명세 개정으로 제거된 {@code budgetType}만 보내면 {@code budget} 누락으로 거부된다. */
    @Test
    void 명세_개정으로_제거된_budgetType만_보내면_거부된다() throws Exception {
        CourseCreationRequest request = MAPPER.readValue("""
                {"destinationCountry":"일본","destinationCity":"오사카","startDate":"2026-09-01",
                 "totalDays":3,"budgetType":"moderate"}
                """, CourseCreationRequest.class);

        assertThat(request.budget()).isNull();
        assertThat(violatedFields(request)).containsExactly("budget");
    }

    @Test
    void 예산이_0이하면_거부된다() {
        assertThat(violatedFields(new CourseCreationRequest("일본", "오사카", "2026-09-01", 3, BigDecimal.ZERO)))
                .containsExactly("budget");
        assertThat(violatedFields(new CourseCreationRequest("일본", "오사카", "2026-09-01", 3,
                BigDecimal.valueOf(-1))))
                .containsExactly("budget");
    }

    /** 명세가 number라 소수 예산(외화)도 잘리지 않고 그대로 보존돼야 한다. */
    @Test
    void 소수_예산도_잘리지_않고_받는다() throws Exception {
        CourseCreationRequest request = MAPPER.readValue("""
                {"destinationCountry":"미국","destinationCity":"뉴욕","startDate":"2026-09-01",
                 "totalDays":3,"budget":1500.75}
                """, CourseCreationRequest.class);

        assertThat(request.budget()).isEqualByComparingTo("1500.75");
        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void 필수_조건이_비면_모두_거부된다() {
        CourseCreationRequest request = new CourseCreationRequest("", "", "2026/09/01", 0, null);

        assertThat(violatedFields(request))
                .containsExactlyInAnyOrder("destinationCountry", "destinationCity", "startDate",
                        "totalDays", "budget");
    }
}
