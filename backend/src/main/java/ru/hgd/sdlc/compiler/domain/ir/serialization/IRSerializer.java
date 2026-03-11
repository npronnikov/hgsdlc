package ru.hgd.sdlc.compiler.domain.ir.serialization;

import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;

/**
 * Interface for serializing and deserializing compiled IR.
 * Per ADR-002: IR must be serializable for storage and transfer.
 */
public interface IRSerializer {

    /**
     * Serializes a compiled IR to a string format.
     *
     * @param ir the compiled IR to serialize
     * @return the serialized string representation
     * @throws SerializationException if serialization fails
     */
    String serialize(CompiledIR ir) throws SerializationException;

    /**
     * Deserializes a string to a compiled IR.
     *
     * @param data the serialized string data
     * @return the deserialized compiled IR
     * @throws SerializationException if deserialization fails
     */
    CompiledIR deserialize(String data) throws SerializationException;

    /**
     * Gets the content type for this serialization format.
     *
     * @return the MIME content type
     */
    String contentType();
}
