package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;
import ru.hgd.sdlc.compiler.domain.ir.serialization.IRSerializer;
import ru.hgd.sdlc.compiler.domain.ir.serialization.JsonIRSerializer;
import ru.hgd.sdlc.compiler.domain.ir.serialization.SerializationException;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;

import java.util.Objects;

/**
 * Application service for serializing and deserializing compiled IR.
 * Provides JSON serialization with type safety.
 *
 * <p>This service handles the serialization stage of the compilation pipeline:
 * CompiledIR -> JSON string (for storage/transfer)
 *
 * <p>Per ADR-002: IR must be serializable for storage and transfer.
 */
@Service
public class SerializationService {

    private final JsonIRSerializer serializer;

    public SerializationService() {
        this.serializer = new JsonIRSerializer(false);
    }

    /**
     * Creates a SerializationService with configurable pretty printing.
     *
     * @param prettyPrint whether to format JSON with indentation
     */
    public SerializationService(boolean prettyPrint) {
        this.serializer = new JsonIRSerializer(prettyPrint);
    }

    /**
     * Serializes a compiled IR to a JSON string.
     *
     * @param ir the compiled IR to serialize
     * @return the JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String serialize(CompiledIR ir) throws SerializationException {
        Objects.requireNonNull(ir, "ir cannot be null");
        return serializer.serialize(ir);
    }

    /**
     * Deserializes a JSON string to a compiled IR.
     *
     * @param data the JSON string data
     * @return the deserialized compiled IR
     * @throws SerializationException if deserialization fails
     */
    public CompiledIR deserialize(String data) throws SerializationException {
        Objects.requireNonNull(data, "data cannot be null");
        return serializer.deserialize(data);
    }

    /**
     * Deserializes a JSON string to a specific IR type.
     *
     * @param data the JSON string data
     * @param type the expected IR type
     * @param <T>  the type of compiled IR
     * @return the deserialized compiled IR
     * @throws SerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public <T extends CompiledIR> T deserialize(String data, Class<T> type) throws SerializationException {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        CompiledIR ir = serializer.deserialize(data);

        if (!type.isInstance(ir)) {
            throw new SerializationException(
                "Type mismatch: expected " + type.getName() +
                " but got " + ir.getClass().getName()
            );
        }

        return (T) ir;
    }

    /**
     * Deserializes a JSON string to a FlowIr.
     *
     * @param data the JSON string data
     * @return the deserialized FlowIr
     * @throws SerializationException if deserialization fails
     */
    public FlowIr deserializeFlow(String data) throws SerializationException {
        Objects.requireNonNull(data, "data cannot be null");
        return serializer.deserializeFlow(data);
    }

    /**
     * Deserializes a JSON string to a SkillIr.
     *
     * @param data the JSON string data
     * @return the deserialized SkillIr
     * @throws SerializationException if deserialization fails
     */
    public ru.hgd.sdlc.compiler.domain.compiler.SkillIr deserializeSkill(String data) throws SerializationException {
        Objects.requireNonNull(data, "data cannot be null");
        return serializer.deserializeSkill(data);
    }

    /**
     * Returns the content type for this serialization format.
     *
     * @return the MIME content type (application/json)
     */
    public String contentType() {
        return serializer.contentType();
    }

    /**
     * Safely serializes IR, returning null on failure instead of throwing.
     *
     * @param ir the compiled IR to serialize
     * @return the JSON string, or null if serialization fails
     */
    public String serializeOrNull(CompiledIR ir) {
        if (ir == null) {
            return null;
        }
        try {
            return serialize(ir);
        } catch (SerializationException e) {
            return null;
        }
    }

    /**
     * Safely deserializes JSON, returning null on failure instead of throwing.
     *
     * @param data the JSON string data
     * @return the deserialized IR, or null if deserialization fails
     */
    public CompiledIR deserializeOrNull(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return deserialize(data);
        } catch (SerializationException e) {
            return null;
        }
    }

    /**
     * Checks if a string appears to be valid serialized IR.
     *
     * @param data the string to check
     * @return true if the string can be deserialized
     */
    public boolean isValidSerializedIR(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }
        try {
            deserialize(data);
            return true;
        } catch (SerializationException e) {
            return false;
        }
    }
}
