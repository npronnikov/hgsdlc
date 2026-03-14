package ru.hgd.sdlc.idempotency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.idempotency.domain.IdempotencyRecord;
import ru.hgd.sdlc.idempotency.infrastructure.IdempotencyRecordRepository;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String hashPayload(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return ChecksumUtil.sha256(json);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Failed to serialize idempotency payload");
        }
    }

    @Transactional
    public <T> T execute(
            String idempotencyKey,
            String scope,
            String requestHash,
            Class<T> responseType,
            Supplier<T> supplier
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key is required");
        }
        if (scope == null || scope.isBlank()) {
            throw new ValidationException("Idempotency scope is required");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new ValidationException("Idempotency request hash is required");
        }

        IdempotencyRecord existing = repository.findByIdempotencyKeyAndScope(idempotencyKey, scope).orElse(null);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new ConflictException("Idempotency key reused with different request");
            }
            if (existing.getResponseJson() == null) {
                throw new ConflictException("Idempotency key is already in progress");
            }
            return deserialize(existing.getResponseJson(), responseType);
        }

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .scope(scope)
                .requestHash(requestHash)
                .createdAt(Instant.now())
                .build();

        try {
            repository.save(record);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyRecord concurrent = repository.findByIdempotencyKeyAndScope(idempotencyKey, scope)
                    .orElseThrow(() -> ex);
            if (!requestHash.equals(concurrent.getRequestHash())) {
                throw new ConflictException("Idempotency key reused with different request");
            }
            if (concurrent.getResponseJson() == null) {
                throw new ConflictException("Idempotency key is already in progress");
            }
            return deserialize(concurrent.getResponseJson(), responseType);
        }

        try {
            T response = supplier.get();
            record.setResponseJson(objectMapper.valueToTree(response));
            record.setCompletedAt(Instant.now());
            repository.save(record);
            return response;
        } catch (RuntimeException ex) {
            repository.delete(record);
            throw ex;
        }
    }

    private <T> T deserialize(JsonNode jsonNode, Class<T> responseType) {
        try {
            return objectMapper.treeToValue(jsonNode, responseType);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Failed to deserialize idempotent response");
        }
    }
}
