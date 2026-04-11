package ru.hgd.sdlc.benchmark.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.benchmark.domain.BenchmarkRunEntity;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRunEntity, UUID> {
    List<BenchmarkRunEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    List<BenchmarkRunEntity> findAllByOrderByCreatedAtDesc();

    void deleteByCaseId(UUID caseId);
}
