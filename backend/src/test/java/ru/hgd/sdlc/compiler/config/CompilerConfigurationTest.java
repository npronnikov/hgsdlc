package ru.hgd.sdlc.compiler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.hgd.sdlc.compiler.domain.ir.serialization.JsonIRSerializer;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.compiler.domain.validation.CompositeValidator;

import static org.assertj.core.api.Assertions.assertThat;

class CompilerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(CompilerProperties.class)
    @org.springframework.context.annotation.Import(CompilerConfiguration.class)
    static class TestConfig {
    }

    @Nested
    @DisplayName("Configuration properties")
    class ConfigurationProperties {

        @Test
        @DisplayName("should register CompilerProperties bean")
        void shouldRegisterCompilerPropertiesBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(CompilerProperties.class);
            });
        }
    }

    @Nested
    @DisplayName("IR serialization beans")
    class IrSerializationBeans {

        @Test
        @DisplayName("should create JsonIRSerializer bean")
        void shouldCreateJsonIRSerializerBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(JsonIRSerializer.class);
                assertThat(context.getBean(JsonIRSerializer.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create irObjectMapper bean")
        void shouldCreateIrObjectMapperBean() {
            contextRunner.run(context -> {
                assertThat(context.containsBean("irObjectMapper")).isTrue();
                ObjectMapper mapper = (ObjectMapper) context.getBean("irObjectMapper");
                assertThat(mapper).isNotNull();
            });
        }

        @Test
        @DisplayName("should configure irObjectMapper with CompilerModule")
        void shouldConfigureIrObjectMapperWithCompilerModule() {
            contextRunner.run(context -> {
                ObjectMapper mapper = (ObjectMapper) context.getBean("irObjectMapper");
                assertThat(mapper.getRegisteredModuleIds())
                        .contains("CompilerModule");
            });
        }
    }

    @Nested
    @DisplayName("Parser beans import")
    class ParserBeansImport {

        @Test
        @DisplayName("should have FrontmatterExtractor from ParserConfiguration")
        void shouldHaveFrontmatterExtractor() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(FrontmatterExtractor.class);
            });
        }

        @Test
        @DisplayName("should have FlowParser from ParserConfiguration")
        void shouldHaveFlowParser() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(FlowParser.class);
            });
        }

        @Test
        @DisplayName("should have SkillParser from ParserConfiguration")
        void shouldHaveSkillParser() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SkillParser.class);
            });
        }
    }

    @Nested
    @DisplayName("Validator beans import")
    class ValidatorBeansImport {

        @Test
        @DisplayName("should have CompositeValidator from ValidatorConfiguration")
        void shouldHaveCompositeValidator() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(CompositeValidator.class);
            });
        }
    }

    @Nested
    @DisplayName("Pretty print configuration")
    class PrettyPrintConfiguration {

        @Test
        @DisplayName("should not enable pretty print by default")
        void shouldNotEnablePrettyPrintByDefault() {
            contextRunner.run(context -> {
                ObjectMapper mapper = (ObjectMapper) context.getBean("irObjectMapper");
                // Check that INDENT_OUTPUT is not enabled
                assertThat(mapper.getSerializationConfig()
                        .hasSerializationFeatures(
                                com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT.getMask()))
                        .isFalse();
            });
        }

        @Test
        @DisplayName("should enable pretty print when configured")
        void shouldEnablePrettyPrintWhenConfigured() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.pretty-print=true")
                    .run(context -> {
                        ObjectMapper mapper = (ObjectMapper) context.getBean("irObjectMapper");
                        assertThat(mapper.getSerializationConfig()
                                .hasSerializationFeatures(
                                        com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT.getMask()))
                                .isTrue();
                    });
        }
    }
}
