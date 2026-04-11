package ru.hgd.sdlc.benchmark.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.benchmark.domain.BenchmarkCaseEntity;

public interface BenchmarkCaseRepository extends JpaRepository<BenchmarkCaseEntity, UUID> {
    List<BenchmarkCaseEntity> findAllByOrderByCreatedAtDesc();
}
