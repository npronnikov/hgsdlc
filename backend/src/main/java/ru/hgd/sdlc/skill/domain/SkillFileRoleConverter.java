package ru.hgd.sdlc.skill.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SkillFileRoleConverter implements AttributeConverter<SkillFileRole, String> {
    @Override
    public String convertToDatabaseColumn(SkillFileRole attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toApiValue();
    }

    @Override
    public SkillFileRole convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return SkillFileRole.from(dbData);
    }
}

