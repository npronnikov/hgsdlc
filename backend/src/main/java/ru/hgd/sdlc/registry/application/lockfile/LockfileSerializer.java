package ru.hgd.sdlc.registry.application.lockfile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Objects;

/**
 * Serializer for lockfile JSON format.
 *
 * <p>Provides methods to serialize and deserialize lockfiles
 * to/from JSON strings for storage.
 */
public class LockfileSerializer {

    private final ObjectMapper objectMapper;

    /**
     * Creates a new LockfileSerializer with default configuration.
     */
    public LockfileSerializer() {
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates a new LockfileSerializer with a custom ObjectMapper.
     *
     * @param objectMapper the ObjectMapper to use
     */
    public LockfileSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    /**
     * Serializes a lockfile to JSON.
     *
     * @param lockfile the lockfile to serialize
     * @return the JSON string
     * @throws LockfileSerializationException if serialization fails
     */
    public String toJson(Lockfile lockfile) {
        try {
            return objectMapper.writeValueAsString(lockfile);
        } catch (JsonProcessingException e) {
            throw new LockfileSerializationException("Failed to serialize lockfile", e);
        }
    }

    /**
     * Serializes a lockfile to pretty-printed JSON.
     *
     * @param lockfile the lockfile to serialize
     * @return the pretty-printed JSON string
     * @throws LockfileSerializationException if serialization fails
     */
    public String toJsonPretty(Lockfile lockfile) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(lockfile);
        } catch (JsonProcessingException e) {
            throw new LockfileSerializationException("Failed to serialize lockfile", e);
        }
    }

    /**
     * Deserializes a lockfile from JSON.
     *
     * @param json the JSON string
     * @return the deserialized lockfile
     * @throws LockfileSerializationException if deserialization fails
     */
    public Lockfile fromJson(String json) {
        try {
            return objectMapper.readValue(json, Lockfile.class);
        } catch (JsonProcessingException e) {
            throw new LockfileSerializationException("Failed to deserialize lockfile", e);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
