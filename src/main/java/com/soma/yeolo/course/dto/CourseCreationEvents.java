package com.soma.yeolo.course.dto;

/**
 * API-FB-4 SSE 이벤트 페이로드 정의. step명·필드명은 명세 그대로 사용한다.
 */
public final class CourseCreationEvents {

    private CourseCreationEvents() {
    }

    /** {@code event: progress} 데이터 — 진행 단계와 안내 메시지. */
    public record Progress(String step, String message) {
    }

    /** {@code event: complete}의 {@code data} — 생성·저장된 코스 식별자. (명세 개정: courseId만 반환) */
    public record CompleteData(String courseId) {
    }
}
