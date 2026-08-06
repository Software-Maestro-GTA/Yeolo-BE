package com.soma.yeolo.global.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * {@code List<String>} ↔ JSON 문자열 매핑. DB 배열 타입에 의존하지 않도록 문자열 컬럼(JSON)로 저장한다.
 * (DOM-2 {@code tags TEXT[]}, DOM-3 장소의 {@code photoUrls}/{@code openingHours} 근사)
 *
 * <p>여러 도메인 엔티티가 공유하므로 {@code global.entity}에 둔다. {@code autoApply}는 켜지 않으며,
 * 필요한 필드에 {@code @Convert(converter = StringListJsonConverter.class)}로 명시 적용한다.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize string list", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize string list: " + dbData, e);
        }
    }
}
