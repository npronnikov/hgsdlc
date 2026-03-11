package ru.hgd.sdlc.compiler.domain.ir.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;

/**
 * JSON-based implementation of IRSerializer using Jackson.
 * Supports polymorphic serialization with type discriminators.
 */
public class JsonIRSerializer implements IRSerializer {

    private static final String CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;

    /**
     * Creates a new JsonIRSerializer with default configuration.
     */
    public JsonIRSerializer() {
        this(false);
    }

    /**
     * Creates a new JsonIRSerializer with optional pretty printing.
     *
     * @param prettyPrint whether to format JSON with indentation
     */
    public JsonIRSerializer(boolean prettyPrint) {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(CompiledIR.class)
            .allowIfSubType(FlowIr.class)
            .allowIfSubType(SkillIr.class)
            .allowIfSubType("ru.hgd.sdlc.compiler.domain.model.ir")
            .allowIfSubType("ru.hgd.sdlc.compiler.domain.compiler")
            .allowIfSubType("java.util")
            .allowIfSubType("java.time")
            .allowIfSubType("java.lang")
            .build();

        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new CompilerModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        if (prettyPrint) {
            this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
    }

    @Override
    public String serialize(CompiledIR ir) throws SerializationException {
        try {
            return objectMapper.writeValueAsString(ir);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize IR: " + e.getMessage(), e);
        }
    }

    @Override
    public CompiledIR deserialize(String data) throws SerializationException {
        try {
            return objectMapper.readValue(data, CompiledIR.class);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize IR: " + e.getMessage(), e);
        }
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    /**
     * Deserializes a FlowIr from JSON.
     *
     * @param data the JSON string
     * @return the deserialized FlowIr
     * @throws SerializationException if deserialization fails
     */
    public FlowIr deserializeFlow(String data) throws SerializationException {
        try {
            return objectMapper.readValue(data, FlowIr.class);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize FlowIr: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a SkillIr from JSON.
     *
     * @param data the JSON string
     * @return the deserialized SkillIr
     * @throws SerializationException if deserialization fails
     */
    public SkillIr deserializeSkill(String data) throws SerializationException {
        try {
            return objectMapper.readValue(data, SkillIr.class);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize SkillIr: " + e.getMessage(), e);
        }
    }
}
