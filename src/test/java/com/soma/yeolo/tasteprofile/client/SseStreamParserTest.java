package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SseStreamParserTest {

    @Test
    void 지정한_이벤트의_data_페이로드를_추출한다() {
        String stream = """
                event: progress
                data: {"step":"ANALYZING_PREFERENCE"}

                event: complete
                data: {"tasteProfile":{"sourceType":"behavior"}}

                """;

        String data = SseStreamParser.dataOfEvent(stream, "complete");

        assertThat(data).isEqualTo("{\"tasteProfile\":{\"sourceType\":\"behavior\"}}");
    }

    @Test
    void 마지막_블록에_개행이_없어도_추출한다() {
        String stream = "event: complete\ndata: {\"tasteProfile\":{}}";

        assertThat(SseStreamParser.dataOfEvent(stream, "complete")).isEqualTo("{\"tasteProfile\":{}}");
    }

    @Test
    void CRLF_개행도_처리한다() {
        String stream = "event: complete\r\ndata: {\"ok\":true}\r\n\r\n";

        assertThat(SseStreamParser.dataOfEvent(stream, "complete")).isEqualTo("{\"ok\":true}");
    }

    @Test
    void 이벤트가_없으면_null을_반환한다() {
        String stream = "event: progress\ndata: {}\n\n";

        assertThat(SseStreamParser.dataOfEvent(stream, "complete")).isNull();
        assertThat(SseStreamParser.dataOfEvent(null, "complete")).isNull();
    }
}
