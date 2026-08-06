package com.soma.yeolo.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 API 응답 JSON에서 값을 꺼낼 때 쓰는 공용 헬퍼.
 *
 * <p>외부 응답은 필드가 없거나 {@code null}이거나 타입이 다를 수 있어, 어댑터마다 같은 방어 로직을
 * 반복하게 된다. "없으면 null / 빈 목록"이라는 한 가지 규칙으로 통일해 어댑터가 파싱이 아닌 매핑에만
 * 집중하도록 한다.
 */
public final class JsonNodes {

    private JsonNodes() {
    }

    /** 문자열 필드. 없거나 공백뿐이면 null. */
    public static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 숫자 필드. 숫자가 아니면 null. */
    public static Double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    /**
     * 문자열로 표현된 숫자 필드(예: Nominatim의 {@code "lat": "33.45"}). 파싱할 수 없으면 null.
     */
    public static Double decimalText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 문자열 배열 필드. 배열이 아니면 빈 목록. */
    public static List<String> textList(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        array.forEach(item -> values.add(item.asText()));
        return values;
    }
}
