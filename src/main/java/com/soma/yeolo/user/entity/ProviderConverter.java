package com.soma.yeolo.user.entity;

import com.soma.yeolo.user.domain.Provider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link Provider} ↔ DB 소문자 저장값 매핑. */
@Converter(autoApply = true)
public class ProviderConverter implements AttributeConverter<Provider, String> {

    @Override
    public String convertToDatabaseColumn(Provider attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Provider convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Provider.fromValue(dbData);
    }
}
