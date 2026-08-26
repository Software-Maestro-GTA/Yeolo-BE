package com.soma.yeolo.global.sse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * SSE 응답에 중간 프록시가 스트림을 건드리지 못하게 하는 헤더를 붙인다.
 *
 * <p>스트림을 여는 컨트롤러는 emitter를 만들기 <b>전에</b> 이걸 호출한다. 응답이 커밋된 뒤에는
 * 헤더를 더 이상 넣을 수 없다.
 *
 * <p><b>왜 필요한가.</b> 엣지(CloudFront/Cloudflare)가 응답에 압축을 붙이면 이벤트가 버퍼에 모였다가
 * 몰아서 나간다. 실시간성이 깨지는 것으로 끝나지 않는다 — 15초 하트비트({@link SseHeartbeat})가
 * 엣지 버퍼에 갇히면 프록시가 보기에 오리진이 무응답인 상태가 되어, 무활동 타임아웃에 걸려
 * 스트림이 통째로 끊긴다(Cloudflare 524). 이 서비스의 분석 파이프라인은 80초대라 그 상한에
 * 실제로 닿는다.
 *
 * <p>엣지 설정(대시보드의 압축 토글)으로도 막을 수 있지만, 오리진이 직접 선언하는 쪽이 확실하고
 * 설정이 바뀌거나 엣지가 교체돼도 살아남는다.
 */
public final class SseResponses {

    private SseResponses() {
    }

    /**
     * 스트림 응답 헤더를 설정한다. emitter 생성·반환 전에 호출해야 한다.
     *
     * <ul>
     *   <li>{@code no-transform} — 중간 프록시의 압축·변환 금지. 이게 핵심이다.
     *   <li>{@code no-cache, no-store} — 스트림 응답을 캐시하지 않는다.
     *   <li>{@code X-Accel-Buffering: no} — nginx 계열 프록시의 응답 버퍼링 해제.
     *       현재 경로에는 nginx가 없지만 비용이 0이고, 프록시가 바뀌어도 유효하다.
     * </ul>
     */
    public static void applyStreamingHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
