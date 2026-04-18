package ru.hgd.sdlc.rule.application;

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
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;

@Service
public class RuleEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RuleEmbeddingService.class);

    private final RuleVersionRepository ruleVersionRepository;
    private final EmbeddingService embeddingService;

    public RuleEmbeddingService(
            RuleVersionRepository ruleVersionRepository,
            EmbeddingService embeddingService) {
        this.ruleVersionRepository = ruleVersionRepository;
        this.embeddingService = embeddingService;
    }

    @Async
    @Transactional
    public void generateEmbedding(UUID ruleId) {
        try {
            RuleVersion rule = ruleVersionRepository.findById(ruleId).orElse(null);
            if (rule == null) {
                log.warn("Rule not found for embedding generation: {}", ruleId);
                return;
            }

            String textForEmbedding = prepareTextForEmbedding(rule);
            float[] embedding = embeddingService.generateEmbedding(textForEmbedding);

            rule.setEmbeddingVector(embedding);
            ruleVersionRepository.save(rule);

            log.info("Generated embedding for rule: {}", rule.getRuleId());
        } catch (Exception e) {
            log.error("Failed to generate embedding for rule: {}", ruleId, e);
        }
    }

    public List<SimilarItem> findSimilar(UUID ruleId, float threshold, int limit) {
        RuleVersion currentRule = ruleVersionRepository.findById(ruleId).orElse(null);
        if (currentRule == null || currentRule.getEmbeddingVector() == null) {
            log.warn("Rule not found or no embedding available: {}", ruleId);
            return List.of();
        }

        String vectorString = arrayToPgvectorString(currentRule.getEmbeddingVector());
        List<Object[]> results = ruleVersionRepository.findSimilarById(ruleId, vectorString, threshold, limit);

        return mapToSimilarItems(results);
    }

    public List<SimilarItem> findSimilarByText(String text, float threshold, int limit) {
        float[] embedding = embeddingService.generateEmbedding(text);
        String vectorString = arrayToPgvectorString(embedding);

        List<Object[]> results = ruleVersionRepository.findSimilarByText(vectorString, threshold, limit);

        return mapToSimilarItems(results);
    }

    private String prepareTextForEmbedding(RuleVersion rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getTitle()).append("\n");
        sb.append(rule.getDescription() != null ? rule.getDescription() : "").append("\n");
        sb.append(rule.getRuleMarkdown());
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
            String title = (String) row[3];
            String description = (String) row[4];
            Double similarity = (Double) row[5];

            RuleVersion rule = ruleVersionRepository.findById(id).orElse(null);
            if (rule != null) {
                items.add(new SimilarItem(
                    id,
                    itemId,
                    version,
                    title,
                    description,
                    similarity != null ? similarity.floatValue() : 0.0f,
                    rule.getTags(),
                    rule.getTeamCode(),
                    rule.getScope()
                ));
            }
        }
        return items;
    }
}
