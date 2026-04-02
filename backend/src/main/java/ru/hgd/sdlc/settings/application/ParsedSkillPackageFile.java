package ru.hgd.sdlc.settings.application;

import ru.hgd.sdlc.skill.domain.SkillFileRole;

/**
 * Package-private: used in ParsedMetadata and CatalogUpsertService.
 */
record ParsedSkillPackageFile(
        String path,
        SkillFileRole role,
        String mediaType,
        boolean executable,
        String content,
        long sizeBytes
) {}
