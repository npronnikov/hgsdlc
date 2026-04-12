package ru.hgd.sdlc.runtime.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AiSessionModeConverter implements AttributeConverter<AiSessionMode, String> {
    @Override
    public String convertToDatabaseColumn(AiSessionMode attribute) {
        return attribute == null ? null : attribute.apiValue();
    }

    @Override
    public AiSessionMode convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AiSessionMode.fromApiValue(dbData);
    }
}
