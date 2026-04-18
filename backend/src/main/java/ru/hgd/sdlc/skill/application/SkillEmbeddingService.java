package ru.hgd.sdlc.skill.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.embedding.application.EmbeddingService;
import ru.hgd.sdlc.common.embedding.domain.SimilarItem;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class SkillEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SkillEmbeddingService.class);

    private final SkillVersionRepository skillVersionRepository;
    private final EmbeddingService embeddingService;

    public SkillEmbeddingService(
            SkillVersionRepository skillVersionRepository,
            EmbeddingService embeddingService) {
        this.skillVersionRepository = skillVersionRepository;
        this.embeddingService = embeddingService;
    }

    @Async
    @Transactional
    public void generateEmbedding(UUID skillId) {
        try {
            SkillVersion skill = skillVersionRepository.findById(skillId).orElse(null);
            if (skill == null) {
                log.warn("Skill not found for embedding generation: {}", skillId);
                return;
            }

            String textForEmbedding = prepareTextForEmbedding(skill);
            float[] embedding = embeddingService.generateEmbedding(textForEmbedding);

            skill.setEmbeddingVector(embedding);
            skillVersionRepository.save(skill);

            log.info("Generated embedding for skill: {}", skill.getSkillId());
        } catch (Exception e) {
            log.error("Failed to generate embedding for skill: {}", skillId, e);
        }
    }

    public List<SimilarItem> findSimilar(UUID skillId, float threshold, int limit) {
        SkillVersion currentSkill = skillVersionRepository.findById(skillId).orElse(null);
        if (currentSkill == null || currentSkill.getEmbeddingVector() == null) {
            log.warn("Skill not found or no embedding available: {}", skillId);
            return List.of();
        }

        String vectorString = arrayToPgvectorString(currentSkill.getEmbeddingVector());
        List<Object[]> results = skillVersionRepository.findSimilarById(skillId, vectorString, threshold, limit);

        return mapToSimilarItems(results);
    }

    public List<SimilarItem> findSimilarByText(String text, float threshold, int limit) {
        float[] embedding = embeddingService.generateEmbedding(text);
        String vectorString = arrayToPgvectorString(embedding);

        List<Object[]> results = skillVersionRepository.findSimilarByText(vectorString, threshold, limit);

        return mapToSimilarItems(results);
    }

    private String prepareTextForEmbedding(SkillVersion skill) {
        StringBuilder sb = new StringBuilder();
        sb.append(skill.getName()).append("\n");
        sb.append(skill.getDescription()).append("\n");
        sb.append(skill.getSkillMarkdown());
        return sb.toString();
    }

    private String arrayToPgvectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private List<SimilarItem> mapToSimilarItems(List<Object[]> results) {
        List<SimilarItem> items = new ArrayList<>();
        for (Object[] row : results) {
            UUID id = (UUID) row[0];
            String itemId = (String) row[1];
            String version = (String) row[2];
            String name = (String) row[3];
            String description = (String) row[4];
            Double similarity = (Double) row[5];

            SkillVersion skill = skillVersionRepository.findById(id).orElse(null);
            if (skill != null) {
                items.add(new SimilarItem(
                    id,
                    itemId,
                    version,
                    name,
                    description,
                    similarity != null ? similarity.floatValue() : 0.0f,
                    skill.getTags(),
                    skill.getTeamCode(),
                    skill.getScope()
                ));
            }
        }
        return items;
    }
}
