package com.soma.yeolo.tasteprofile.dto;

/**
 * API-PREF-3 SSE 이벤트 페이로드 정의. step명·필드명은 명세 그대로 사용한다.
 */
public final class BehaviorAnalysisEvents {

    private BehaviorAnalysisEvents() {
    }

    /** {@code event: progress} 데이터 — 진행 단계와 안내 메시지. */
    public record Progress(String step, String message) {
    }

    /** {@code event: complete}의 {@code data} — 생성된 취향 프로필 식별자. */
    public record CompleteData(String tasteProfileId) {
    }
}
