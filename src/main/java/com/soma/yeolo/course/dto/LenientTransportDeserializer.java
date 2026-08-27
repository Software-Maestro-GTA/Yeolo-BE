package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.soma.yeolo.course.dto.Itinerary.TransportToNext;
import java.io.IOException;

/**
 * {@code transportToNext}를 객체로 읽되, 문자열이면 이동 수단만 담은 객체로 승격한다.
 *
 * <p>명세 개정 전 {@code transportToNext}는 {@code "walking"} 같은 <b>문자열</b>이었고, 그 시절
 * 저장된 코스가 DB에 남아 있다. 문자열을 객체로 읽으려다 예외가 나면 코스 상세 조회 전체가 500이
 * 되므로, 옛 형식은 {@code type}만 채운 객체로 옮겨 나머지 필드를 {@code null}로 둔다 —
 * 거리·시간·비용은 그 시절 stop에 평평하게 붙어 있었고 명세에서 사라진 값이라 되살리지 않는다.
 */
public class LenientTransportDeserializer extends JsonDeserializer<TransportToNext> {

    @Override
    public TransportToNext deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return context.readValue(parser, TransportToNext.class);
        }
        // 객체도 문자열도 아닌 값(배열 등)은 하위 토큰까지 소비하고 버린다 — 열림 토큰에 커서를 둔 채
        // 돌아가면 상위 역직렬화기가 값 '안쪽'부터 읽어 stop이 조용히 깨진다.
        if (parser.currentToken() != null && parser.currentToken().isStructStart()) {
            parser.skipChildren();
            return null;
        }
        String type = parser.getValueAsString();
        return (type == null || type.isBlank())
                ? null : new TransportToNext(type, null, null, null, null);
    }
}
