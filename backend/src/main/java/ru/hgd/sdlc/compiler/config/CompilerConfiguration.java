package ru.hgd.sdlc.compiler.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;
import ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.ir.serialization.CompilerModule;
import ru.hgd.sdlc.compiler.domain.ir.serialization.JsonIRSerializer;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;

/**
 * Main configuration for the compiler module.
 * Configures IR serialization, imports parser and validator configurations.
 */
@Configuration
@EnableConfigurationProperties(CompilerProperties.class)
@Import({
    ParserConfiguration.class,
    ValidatorConfiguration.class
})
public class CompilerConfiguration {

    private final CompilerProperties properties;

    public CompilerConfiguration(CompilerProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the JsonIRSerializer bean for serializing compiled IR to JSON.
     * Uses the prettyPrint setting from configuration.
     *
     * @return a new JsonIRSerializer instance
     */
    @Bean
    public JsonIRSerializer jsonIRSerializer() {
        return new JsonIRSerializer(properties.isPrettyPrint());
    }

    /**
     * Creates a specialized ObjectMapper for IR serialization.
     * This ObjectMapper is configured with:
     * <ul>
     *   <li>JavaTimeModule for Java 8 date/time types</li>
     *   <li>CompilerModule for compiler domain types</li>
     *   <li>Polymorphic type handling for IR types</li>
     *   <li>Pretty printing based on configuration</li>
     * </ul>
     *
     * @return a configured ObjectMapper for IR serialization
     */
    @Bean("irObjectMapper")
    public ObjectMapper irObjectMapper() {
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

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new CompilerModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        if (properties.isPrettyPrint()) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        return mapper;
    }

    /**
     * Creates the FlowCompiler bean for compiling FlowDocument to FlowIr.
     *
     * @return a new FlowCompiler instance
     */
    @Bean
    public FlowCompiler flowCompiler() {
        return new FlowCompiler();
    }

    /**
     * Creates the SkillCompiler bean for compiling SkillDocument to SkillIr.
     *
     * @return a new SkillCompiler instance
     */
    @Bean
    public SkillCompiler skillCompiler() {
        return new SkillCompiler();
    }
}
