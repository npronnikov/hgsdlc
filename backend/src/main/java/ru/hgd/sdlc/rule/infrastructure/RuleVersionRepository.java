package ru.hgd.sdlc.rule.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {
    Optional<RuleVersion> findFirstByRuleIdAndStatusOrderBySavedAtDesc(String ruleId, RuleStatus status);

    Optional<RuleVersion> findFirstByRuleIdAndVersionOrderBySavedAtDesc(String ruleId, String version);

    Optional<RuleVersion> findFirstByCanonicalNameAndStatus(String canonicalName, RuleStatus status);

    List<RuleVersion> findByRuleIdOrderBySavedAtDesc(String ruleId);

    List<RuleVersion> findAllByOrderBySavedAtDesc();
}
