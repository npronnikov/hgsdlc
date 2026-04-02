package ru.hgd.sdlc.settings.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ru.hgd.sdlc.common.ValidationException;

/**
 * Package-private: shared between CatalogService, CatalogValidationService, CatalogUpsertService.
 */
record ParsedMetadata(
        String entityType,
        String id,
        String version,
        String canonicalName,
        String displayName,
        String sourcePath,
        String content,
        String checksum,
        List<ParsedSkillPackageFile> skillPackageFiles,
        Map<String, Object> raw
) {
    String require(String key) {
        String value = optional(key);
        if (value == null || value.isBlank()) {
            throw new ValidationException("metadata field is required: " + key);
        }
        return value.trim();
    }

    String optional(String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    @SuppressWarnings("unchecked")
    List<String> tags() {
        Object value = raw.get("tags");
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> tags = new ArrayList<>();
            for (Object item : list) {
                String text = String.valueOf(item).trim();
                if (!text.isBlank()) {
                    tags.add(text);
                }
            }
            return tags;
        }
        return List.of();
    }
}
