package ru.hgd.sdlc.runtime.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ReworkSessionPolicyConverter implements AttributeConverter<ReworkSessionPolicy, String> {
    @Override
    public String convertToDatabaseColumn(ReworkSessionPolicy attribute) {
        return attribute == null ? null : attribute.apiValue();
    }

    @Override
    public ReworkSessionPolicy convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReworkSessionPolicy.fromApiValue(dbData);
    }
}
