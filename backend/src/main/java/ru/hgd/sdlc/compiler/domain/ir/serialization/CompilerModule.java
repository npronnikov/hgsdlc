package ru.hgd.sdlc.compiler.domain.ir.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.io.IOException;

/**
 * Jackson module for serializing and deserializing compiler value types.
 */
public class CompilerModule extends SimpleModule {

    public CompilerModule() {
        super("CompilerModule");

        // FlowId
        addSerializer(FlowId.class, new FlowIdSerializer());
        addDeserializer(FlowId.class, new FlowIdDeserializer());

        // SkillId
        addSerializer(SkillId.class, new SkillIdSerializer());
        addDeserializer(SkillId.class, new SkillIdDeserializer());

        // PhaseId
        addSerializer(PhaseId.class, new PhaseIdSerializer());
        addDeserializer(PhaseId.class, new PhaseIdDeserializer());

        // NodeId
        addSerializer(NodeId.class, new NodeIdSerializer());
        addDeserializer(NodeId.class, new NodeIdDeserializer());

        // ArtifactTemplateId
        addSerializer(ArtifactTemplateId.class, new ArtifactTemplateIdSerializer());
        addDeserializer(ArtifactTemplateId.class, new ArtifactTemplateIdDeserializer());

        // SchemaId
        addSerializer(SchemaId.class, new SchemaIdSerializer());
        addDeserializer(SchemaId.class, new SchemaIdDeserializer());

        // Sha256
        addSerializer(Sha256.class, new Sha256Serializer());
        addDeserializer(Sha256.class, new Sha256Deserializer());

        // HandlerRef
        addSerializer(HandlerRef.class, new HandlerRefSerializer());
        addDeserializer(HandlerRef.class, new HandlerRefDeserializer());

        // Role
        addSerializer(Role.class, new RoleSerializer());
        addDeserializer(Role.class, new RoleDeserializer());

        // MarkdownBody
        addSerializer(MarkdownBody.class, new MarkdownBodySerializer());
        addDeserializer(MarkdownBody.class, new MarkdownBodyDeserializer());

        // SemanticVersion
        addSerializer(SemanticVersion.class, new SemanticVersionSerializer());
        addDeserializer(SemanticVersion.class, new SemanticVersionDeserializer());
    }

    // === FlowId ===
    static class FlowIdSerializer extends JsonSerializer<FlowId> {
        @Override
        public void serialize(FlowId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class FlowIdDeserializer extends JsonDeserializer<FlowId> {
        @Override
        public FlowId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return FlowId.of(p.getValueAsString());
        }
    }

    // === SkillId ===
    static class SkillIdSerializer extends JsonSerializer<SkillId> {
        @Override
        public void serialize(SkillId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class SkillIdDeserializer extends JsonDeserializer<SkillId> {
        @Override
        public SkillId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return SkillId.of(p.getValueAsString());
        }
    }

    // === PhaseId ===
    static class PhaseIdSerializer extends JsonSerializer<PhaseId> {
        @Override
        public void serialize(PhaseId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class PhaseIdDeserializer extends JsonDeserializer<PhaseId> {
        @Override
        public PhaseId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return PhaseId.of(p.getValueAsString());
        }
    }

    // === NodeId ===
    static class NodeIdSerializer extends JsonSerializer<NodeId> {
        @Override
        public void serialize(NodeId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class NodeIdDeserializer extends JsonDeserializer<NodeId> {
        @Override
        public NodeId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return NodeId.of(p.getValueAsString());
        }
    }

    // === ArtifactTemplateId ===
    static class ArtifactTemplateIdSerializer extends JsonSerializer<ArtifactTemplateId> {
        @Override
        public void serialize(ArtifactTemplateId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class ArtifactTemplateIdDeserializer extends JsonDeserializer<ArtifactTemplateId> {
        @Override
        public ArtifactTemplateId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return ArtifactTemplateId.of(p.getValueAsString());
        }
    }

    // === SchemaId ===
    static class SchemaIdSerializer extends JsonSerializer<SchemaId> {
        @Override
        public void serialize(SchemaId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class SchemaIdDeserializer extends JsonDeserializer<SchemaId> {
        @Override
        public SchemaId deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return SchemaId.of(p.getValueAsString());
        }
    }

    // === Sha256 ===
    static class Sha256Serializer extends JsonSerializer<Sha256> {
        @Override
        public void serialize(Sha256 value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.hexValue());
        }
    }

    static class Sha256Deserializer extends JsonDeserializer<Sha256> {
        @Override
        public Sha256 deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return Sha256.of(p.getValueAsString());
        }
    }

    // === HandlerRef ===
    static class HandlerRefSerializer extends JsonSerializer<HandlerRef> {
        @Override
        public void serialize(HandlerRef value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toString());
        }
    }

    static class HandlerRefDeserializer extends JsonDeserializer<HandlerRef> {
        @Override
        public HandlerRef deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return HandlerRef.of(p.getValueAsString());
        }
    }

    // === Role ===
    static class RoleSerializer extends JsonSerializer<Role> {
        @Override
        public void serialize(Role value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.value());
        }
    }

    static class RoleDeserializer extends JsonDeserializer<Role> {
        @Override
        public Role deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return Role.of(p.getValueAsString());
        }
    }

    // === MarkdownBody ===
    static class MarkdownBodySerializer extends JsonSerializer<MarkdownBody> {
        @Override
        public void serialize(MarkdownBody value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.content());
        }
    }

    static class MarkdownBodyDeserializer extends JsonDeserializer<MarkdownBody> {
        @Override
        public MarkdownBody deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String content = p.getValueAsString();
            return content != null ? MarkdownBody.of(content) : MarkdownBody.of("");
        }
    }

    // === SemanticVersion ===
    static class SemanticVersionSerializer extends JsonSerializer<SemanticVersion> {
        @Override
        public void serialize(SemanticVersion value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toString());
        }
    }

    static class SemanticVersionDeserializer extends JsonDeserializer<SemanticVersion> {
        @Override
        public SemanticVersion deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return SemanticVersion.of(p.getValueAsString());
        }
    }
}
