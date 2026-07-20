package com.soma.yeolo.tasteprofile.client;

/**
 * 최소 SSE 스트림 파서. AI 내부 API의 짧은 완결 스트림을 통째로 받아, 특정 이벤트명의
 * {@code data:} 페이로드를 추출하는 용도로만 사용한다(무한 스트림 미대상).
 */
final class SseStreamParser {

    private static final String EVENT_PREFIX = "event:";
    private static final String DATA_PREFIX = "data:";

    private SseStreamParser() {
    }

    /**
     * 스트림에서 주어진 이벤트명 블록의 data 페이로드를 반환한다. 없으면 {@code null}.
     * 한 이벤트에 여러 {@code data:} 라인이 있으면 개행으로 이어 붙인다(SSE 규격).
     */
    static String dataOfEvent(String stream, String eventName) {
        if (stream == null) {
            return null;
        }
        String currentEvent = null;
        StringBuilder data = new StringBuilder();
        boolean inTarget = false;

        for (String rawLine : stream.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;

            if (line.isEmpty()) { // 이벤트 경계
                if (inTarget && !data.isEmpty()) {
                    return data.toString();
                }
                currentEvent = null;
                data.setLength(0);
                inTarget = false;
                continue;
            }
            if (line.startsWith(EVENT_PREFIX)) {
                currentEvent = line.substring(EVENT_PREFIX.length()).trim();
                inTarget = eventName.equals(currentEvent);
            } else if (line.startsWith(DATA_PREFIX)) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(DATA_PREFIX.length()).stripLeading());
            }
        }
        // 스트림 끝에 개행이 없어 마지막 블록이 닫히지 않은 경우
        return (inTarget && !data.isEmpty()) ? data.toString() : null;
    }
}
