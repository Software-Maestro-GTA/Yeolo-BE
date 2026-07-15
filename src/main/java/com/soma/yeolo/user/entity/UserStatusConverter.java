package com.soma.yeolo.user.entity;

import com.soma.yeolo.user.domain.UserStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link UserStatus} ↔ DB 소문자 저장값 매핑. */
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserStatus.fromValue(dbData);
    }
}
