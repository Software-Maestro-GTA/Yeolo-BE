package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * 내부 UUID 문자열만 통과시키고, 값이 UUID가 아니면 예외 대신 {@code null}로 떨어뜨린다.
 *
 * <p>저장된 코스 itinerary JSON의 {@code placeId}를 읽을 때 쓴다. 새로 저장되는 코스는 항상 내부
 * UUID를 담지만, 장소 정규화(DOM-3) 이전에 저장된 코스에는 AI가 넣어 보낸 외부 식별자 문자열이 남아
 * 있을 수 있다. 그런 값은 장소 테이블과 연결되지 않아 <b>Google Place ID 로 되돌릴 수 없고</b>, 조회를
 * 깨서도 안 되므로 조용히 버려 "식별자 없는 stop"으로 만든다. 응답에 실리는 Google Place ID 는
 * 이 단계가 아니라 {@code ItineraryGooglePlaceIdMapper}가 장소 테이블에서 채운다.
 *
 * <p>돌려주는 값은 {@link UUID#toString()} 정규형이라, 매퍼가 다시 {@link UUID#fromString}으로 안전하게
 * 되읽을 수 있다.
 */
@Slf4j
public class LenientUuidDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        // 값이 객체·배열이면 그 하위 토큰까지 소비해야 한다. 열림 토큰에 커서를 둔 채 돌아가면
        // 상위 역직렬화기가 값 '안쪽'부터 읽어 stop이 쪼개지는 등 조용히 깨진 결과가 나온다.
        if (parser.currentToken() != null && parser.currentToken().isStructStart()) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException e) {
            log.debug("내부 placeId가 아닌 값이라 응답에서 제외한다: {}", value);
            return null;
        }
    }
}
