package ru.hgd.sdlc.common.embedding.application;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;

@Service
@ConditionalOnProperty(
    name = "embedding.migration.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class EmbeddingMigrationService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingMigrationService.class);
    private static final String MIGRATION_FLAG = "embedding_migration_completed";

    private final SkillVersionRepository skillVersionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final EmbeddingService embeddingService;

    public EmbeddingMigrationService(
            SkillVersionRepository skillVersionRepository,
            RuleVersionRepository ruleVersionRepository,
            EmbeddingService embeddingService) {
        this.skillVersionRepository = skillVersionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    @Transactional
    public void migratePublishedSkills() {
        if (isMigrationCompleted(MIGRATION_FLAG + "_skills")) {
            log.info("Skills migration already completed, skipping");
            return;
        }

        log.info("Starting migration of published skills to embeddings");
        List<SkillVersion> publishedSkills = skillVersionRepository.findAllByOrderBySavedAtDesc()
            .stream()
            .filter(s -> s.getStatus() == SkillStatus.PUBLISHED && s.getEmbeddingVector() == null)
            .toList();

        log.info("Found {} published skills without embeddings", publishedSkills.size());

        int processed = 0;
        for (SkillVersion skill : publishedSkills) {
            try {
                String textForEmbedding = prepareTextForEmbedding(skill);
                float[] embedding = embeddingService.generateEmbedding(textForEmbedding);
                skill.setEmbeddingVector(embedding);
                skillVersionRepository.save(skill);
                processed++;
                if (processed % 10 == 0) {
                    log.info("Migrated {} / {} skills", processed, publishedSkills.size());
                }
            } catch (Exception e) {
                log.error("Failed to migrate skill: {}", skill.getSkillId(), e);
            }
        }

        log.info("Skills migration completed: {} / {} processed", processed, publishedSkills.size());
        markMigrationCompleted(MIGRATION_FLAG + "_skills");
    }

    @PostConstruct
    @Transactional
    public void migratePublishedRules() {
        if (isMigrationCompleted(MIGRATION_FLAG + "_rules")) {
            log.info("Rules migration already completed, skipping");
            return;
        }

        log.info("Starting migration of published rules to embeddings");
        List<RuleVersion> publishedRules = ruleVersionRepository.findAllByOrderBySavedAtDesc()
            .stream()
            .filter(r -> r.getStatus() == RuleStatus.PUBLISHED && r.getEmbeddingVector() == null)
            .toList();

        log.info("Found {} published rules without embeddings", publishedRules.size());

        int processed = 0;
        for (RuleVersion rule : publishedRules) {
            try {
                String textForEmbedding = prepareTextForEmbedding(rule);
                float[] embedding = embeddingService.generateEmbedding(textForEmbedding);
                rule.setEmbeddingVector(embedding);
                ruleVersionRepository.save(rule);
                processed++;
                if (processed % 10 == 0) {
                    log.info("Migrated {} / {} rules", processed, publishedRules.size());
                }
            } catch (Exception e) {
                log.error("Failed to migrate rule: {}", rule.getRuleId(), e);
            }
        }

        log.info("Rules migration completed: {} / {} processed", processed, publishedRules.size());
        markMigrationCompleted(MIGRATION_FLAG + "_rules");
    }

    private String prepareTextForEmbedding(SkillVersion skill) {
        StringBuilder sb = new StringBuilder();
        sb.append(skill.getName()).append("\n");
        sb.append(skill.getDescription()).append("\n");
        sb.append(skill.getSkillMarkdown());
        return sb.toString();
    }

    private String prepareTextForEmbedding(RuleVersion rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getTitle()).append("\n");
        sb.append(rule.getDescription() != null ? rule.getDescription() : "").append("\n");
        sb.append(rule.getRuleMarkdown());
        return sb.toString();
    }

    private boolean isMigrationCompleted(String flag) {
        // TODO: Implement check using SystemSetting repository
        // For now, always return false to allow migration
        return false;
    }

    private void markMigrationCompleted(String flag) {
        // TODO: Implement flag storage using SystemSetting repository
        log.info("Migration flag would be set: {}", flag);
    }
}
