package com.soma.yeolo.global.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseEmittersTest {

    private static final UUID USER_ID = UUID.fromString("522e82ef-be08-4878-9c8a-99f8f769fd56");

    @Mock
    private SseEmitter emitter;

    @Test
    void 에러_콜백은_complete를_부르지_않는다() {
        SseEmitters.registerCallbacks(emitter, "taste-profile", USER_ID);

        errorCallback().accept(new IllegalStateException("Broken pipe"));

        // 이 시점의 complete()는 AsyncRequestNotUsableException을 던지고, 그 예외가 서블릿
        // 에러 디스패치를 타고 전역 핸들러까지 올라가 500이 된다.
        verify(emitter, never()).complete();
    }

    @Test
    void 타임아웃_콜백은_스트림을_정상_종료한다() {
        SseEmitters.registerCallbacks(emitter, "course", USER_ID);

        timeoutCallback().run();

        verify(emitter, times(1)).complete();
    }

    @Test
    void 타임아웃_콜백의_complete가_실패해도_예외를_던지지_않는다() {
        // AsyncRequestNotUsableException은 checked라 doThrow로는 스텁이 안 된다.
        // (Spring도 complete() 선언과 무관하게 이 예외를 흘려보낸다.)
        doAnswer(invocation -> {
            throw new AsyncRequestNotUsableException("Response not usable after response errors.");
        }).when(emitter).complete();
        SseEmitters.registerCallbacks(emitter, "course", USER_ID);

        timeoutCallback().run();
    }

    private Consumer<Throwable> errorCallback() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(emitter).onError(captor.capture());
        return captor.getValue();
    }

    private Runnable timeoutCallback() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(captor.capture());
        return captor.getValue();
    }

    @Test
    void 완료_콜백도_함께_등록한다() {
        SseEmitters.registerCallbacks(emitter, "course", USER_ID);

        verify(emitter).onCompletion(any(Runnable.class));
    }
}
