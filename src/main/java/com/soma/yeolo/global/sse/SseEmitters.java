package com.soma.yeolo.global.sse;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE emitter 생성과 수명주기 콜백 등록을 한곳에 모은 팩토리.
 *
 * <p>emitter 참조가 방치되지 않도록 종료/타임아웃/에러 콜백을 항상 등록한다.
 * 블로킹 AI 호출은 도중 취소가 불가하므로, 콜백은 정리·로깅까지만 책임진다.
 *
 * <p><b>{@code onError}에서는 절대 {@code complete()}를 부르지 않는다.</b> 이 콜백은 응답이 이미
 * 에러 상태로 넘어간 뒤에 실행되며, 그 시점의 {@code complete()}는 응답 버퍼를 flush하려다
 * {@code AsyncRequestNotUsableException}을 던진다. 그 예외는 콜백 밖으로 빠져나가 서블릿 에러
 * 디스패치를 타고 전역 예외 핸들러까지 올라가고, 이미 {@code text/event-stream}으로 고정된 응답에
 * JSON 봉투를 쓰려다 {@code HttpMessageNotWritableException}으로 500이 찍힌다.
 * 에러 이후의 완료 처리는 컨테이너가 알아서 하므로 여기서는 로그만 남긴다.
 */
@Slf4j
public final class SseEmitters {

    private SseEmitters() {
    }

    /**
     * 수명주기 콜백이 붙은 emitter를 만든다.
     *
     * @param streamName 로그에 찍을 스트림 이름(예: {@code "course"}, {@code "taste-profile"})
     * @param timeoutMs  SSE 타임아웃(ms)
     * @param userId     로그 상관용 사용자 식별자
     */
    public static SseEmitter create(String streamName, long timeoutMs, UUID userId) {
        return registerCallbacks(new SseEmitter(timeoutMs), streamName, userId);
    }

    /** 콜백 등록부. 테스트에서 emitter를 주입해 콜백 동작을 검증할 수 있도록 분리한다. */
    static SseEmitter registerCallbacks(SseEmitter emitter, String streamName, UUID userId) {
        // 타임아웃은 아직 응답을 쓸 수 있는 상태이므로 정상 종료로 마무리한다.
        // 여기서 완료하지 않으면 AsyncRequestTimeoutException이 전역 핸들러까지 올라간다.
        emitter.onTimeout(() -> {
            log.warn("SSE 타임아웃 - {} 스트림 (userId={})", streamName, userId);
            completeQuietly(emitter, streamName);
        });
        // 클라이언트 이탈(Broken pipe)은 장애가 아니라 정상적인 종료 사유다.
        // 스택 트레이스 없이 한 줄만 남긴다.
        emitter.onError(e ->
                log.warn("SSE 에러 - {} 스트림 (userId={}): {}", streamName, userId, e.toString()));
        emitter.onCompletion(() -> log.debug("SSE 종료 - {} 스트림 (userId={})", streamName, userId));
        return emitter;
    }

    /** 이미 끊긴 연결이면 완료 처리도 실패할 수 있다. 콜백 밖으로 던지지 않는다. */
    private static void completeQuietly(SseEmitter emitter, String streamName) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 실패 - {} 스트림: {}", streamName, e.toString());
        }
    }
}
