package com.soma.yeolo.global.client;

/**
 * 연속 호출 사이에 최소 간격을 강제하는 단순 레이트 리미터.
 *
 * <p>Nominatim 공개 서버처럼 "초당 1회 이하" 정책을 요구하는 외부 API를 여러 스레드
 * (예: {@code sse-taste-*})가 동시에 호출할 때, 전역 단일 인스턴스로 호출을 직렬화해
 * 정책을 지킨다. 첫 호출은 대기 없이 통과하고, 이후 호출은 직전 호출로부터
 * {@code intervalMillis}가 지날 때까지 호출 스레드를 블로킹한다.
 *
 * <p>블로킹은 배경 작업 스레드에서 일어나므로 요청 처리 스레드를 잡아두지 않는다.
 * {@code intervalMillis <= 0}이면 아무 대기도 하지 않는다(자체 호스팅 서버용).
 */
public final class IntervalRateLimiter {

    private final long intervalNanos;
    private final Object lock = new Object();

    /** 다음 호출이 허용되는 시각(nanoTime 기준). 첫 호출 전에는 null. */
    private Long nextAllowedNanos = null;

    public IntervalRateLimiter(long intervalMillis) {
        this.intervalNanos = Math.max(0L, intervalMillis) * 1_000_000L;
    }

    /**
     * 직전 호출과의 간격이 최소 간격 이상이 되도록 필요한 만큼 현재 스레드를 블로킹한다.
     *
     * @throws IllegalStateException 대기 중 인터럽트된 경우(인터럽트 플래그는 복원한다)
     */
    public void acquire() {
        if (intervalNanos == 0L) {
            return;
        }
        synchronized (lock) {
            if (nextAllowedNanos != null) {
                long waitNanos = nextAllowedNanos - System.nanoTime();
                if (waitNanos > 0L) {
                    sleep(waitNanos);
                }
            }
            nextAllowedNanos = System.nanoTime() + intervalNanos;
        }
    }

    private void sleep(long waitNanos) {
        try {
            Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rate limiter interrupted while waiting", e);
        }
    }
}
