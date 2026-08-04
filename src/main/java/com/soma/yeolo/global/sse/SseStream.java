package com.soma.yeolo.global.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link SseEmitter} 위에 "한 번 끊기면 다시 쓰지 않는다"는 규칙을 씌운 래퍼.
 *
 * <p>클라이언트가 먼저 연결을 끊으면(브라우저 이탈·프록시 idle timeout) 다음 전송은
 * {@code Broken pipe}로 실패하고, 컨테이너는 {@code onError} 콜백을 태워 emitter를 완료 처리한다.
 * 이 상태에서 다시 {@code send}를 시도하면 {@code IllegalStateException: already completed}가 나고,
 * 그 예외를 {@code completeWithError}로 넘기면 서블릿으로 재디스패치되어 전역 예외 핸들러까지
 * 올라간다 — 이미 {@code text/event-stream}으로 고정된 응답에 JSON 봉투를 쓰려다
 * {@code HttpMessageNotWritableException}으로 500이 찍히는 경로다.
 *
 * <p>그래서 전송 실패는 예외로 전파하지 않고 스트림을 닫힘으로 표시한 뒤 {@code false}만 반환한다.
 * 클라이언트 이탈은 장애가 아니라 정상적인 종료 사유이므로 호출부도 이를 에러로 다루지 않는다.
 * heartbeat 스레드와 워커 스레드가 동시에 쓸 수 있어 전송은 직렬화한다.
 */
@Slf4j
public class SseStream {

    private final SseEmitter emitter;
    private final Object lock = new Object();

    private boolean open = true;

    public SseStream(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** 명세의 이름 있는 이벤트를 전송한다. 이미 끊겼거나 전송에 실패하면 {@code false}. */
    public boolean send(String eventName, Object payload) {
        return write(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
    }

    /**
     * SSE 주석(`:` 라인) 한 줄을 흘려보내 연결을 idle 상태에서 벗어나게 한다.
     * 규격상 클라이언트가 무시하는 라인이라 명세의 이벤트 계약에는 영향이 없다.
     */
    public boolean sendHeartbeat() {
        return write(SseEmitter.event().comment("keepalive"));
    }

    /** 스트림을 정상 종료한다. 이미 끊겼으면 아무것도 하지 않는다. */
    public void complete() {
        synchronized (lock) {
            if (!open) {
                return;
            }
            open = false;
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete 실패(이미 종료된 스트림): {}", e.toString());
            }
        }
    }

    private boolean write(SseEmitter.SseEventBuilder event) {
        synchronized (lock) {
            if (!open) {
                return false;
            }
            try {
                emitter.send(event);
                return true;
            } catch (Exception e) {
                // IOException(클라이언트 이탈) / IllegalStateException(이미 완료) 모두 재시도 불가.
                // completeWithError로 넘기면 서블릿으로 되던져지므로 여기서 끊고 삼킨다.
                open = false;
                log.debug("SSE 전송 실패 - 스트림을 닫는다: {}", e.toString());
                return false;
            }
        }
    }
}
