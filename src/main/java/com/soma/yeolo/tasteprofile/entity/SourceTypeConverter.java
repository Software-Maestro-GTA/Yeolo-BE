package com.soma.yeolo.tasteprofile.entity;

import com.soma.yeolo.tasteprofile.domain.SourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link SourceType} ↔ DB 소문자 저장값(survey/behavior/mixed) 매핑. */
@Converter(autoApply = true)
public class SourceTypeConverter implements AttributeConverter<SourceType, String> {

    @Override
    public String convertToDatabaseColumn(SourceType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public SourceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SourceType.fromValue(dbData);
    }
}
