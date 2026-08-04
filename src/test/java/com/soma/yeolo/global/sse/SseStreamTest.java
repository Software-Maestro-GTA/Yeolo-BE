package com.soma.yeolo.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@ExtendWith(MockitoExtension.class)
class SseStreamTest {

    @Mock
    private SseEmitter emitter;

    @Test
    void 정상_전송하면_이어지는_전송도_그대로_통과한다() throws Exception {
        SseStream stream = new SseStream(emitter);

        assertThat(stream.send("progress", "payload")).isTrue();
        assertThat(stream.send("complete", "payload")).isTrue();
        verify(emitter, times(2)).send(any(SseEventBuilder.class));
    }

    @Test
    void 클라이언트가_끊기면_스트림을_닫고_다시_전송하지_않는다() throws Exception {
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEventBuilder.class));
        SseStream stream = new SseStream(emitter);

        assertThat(stream.send("progress", "payload")).isFalse();

        // 끊긴 뒤의 전송은 emitter를 건드리지 않는다 — 여기서 다시 send하면
        // IllegalStateException(already completed)가 나고 500으로 번진다.
        assertThat(stream.send("complete", "payload")).isFalse();
        assertThat(stream.sendHeartbeat()).isFalse();
        verify(emitter, times(1)).send(any(SseEventBuilder.class));
    }

    @Test
    void 전송_실패를_completeWithError로_되던지지_않는다() throws Exception {
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(emitter).send(any(SseEventBuilder.class));
        SseStream stream = new SseStream(emitter);

        assertThat(stream.send("error", "payload")).isFalse();

        // completeWithError는 서블릿으로 예외를 재디스패치해 전역 핸들러까지 올린다. 호출 금지.
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void 끊긴_스트림의_complete는_emitter를_건드리지_않는다() throws Exception {
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEventBuilder.class));
        SseStream stream = new SseStream(emitter);
        stream.send("progress", "payload");

        stream.complete();

        verify(emitter, never()).complete();
    }

    @Test
    void complete는_한_번만_전달되고_이후_전송도_막는다() throws Exception {
        SseStream stream = new SseStream(emitter);

        stream.complete();
        stream.complete();

        verify(emitter, times(1)).complete();
        assertThat(stream.send("complete", "payload")).isFalse();
        verify(emitter, never()).send(any(SseEventBuilder.class));
    }
}
