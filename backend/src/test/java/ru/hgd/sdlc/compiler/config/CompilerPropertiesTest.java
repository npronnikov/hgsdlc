package ru.hgd.sdlc.compiler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class CompilerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(CompilerProperties.class)
    static class TestConfig {
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should have default values when not configured")
        void shouldHaveDefaultValues() {
            contextRunner.run(context -> {
                CompilerProperties props = context.getBean(CompilerProperties.class);

                assertThat(props.isStrictMode()).isFalse();
                assertThat(props.getDefaultSerializationFormat())
                        .isEqualTo(CompilerProperties.SerializationFormat.JSON);
                assertThat(props.isCacheEnabled()).isTrue();
                assertThat(props.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
                assertThat(props.isPrettyPrint()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("Property binding")
    class PropertyBinding {

        @Test
        @DisplayName("should bind strict-mode property")
        void shouldBindStrictMode() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.strict-mode=true")
                    .run(context -> {
                        CompilerProperties props = context.getBean(CompilerProperties.class);
                        assertThat(props.isStrictMode()).isTrue();
                    });
        }

        @Test
        @DisplayName("should bind default-serialization-format property")
        void shouldBindSerializationFormat() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.default-serialization-format=yaml")
                    .run(context -> {
                        CompilerProperties props = context.getBean(CompilerProperties.class);
                        assertThat(props.getDefaultSerializationFormat())
                                .isEqualTo(CompilerProperties.SerializationFormat.YAML);
                    });
        }

        @Test
        @DisplayName("should bind cache-enabled property")
        void shouldBindCacheEnabled() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.cache-enabled=false")
                    .run(context -> {
                        CompilerProperties props = context.getBean(CompilerProperties.class);
                        assertThat(props.isCacheEnabled()).isFalse();
                    });
        }

        @Test
        @DisplayName("should bind max-file-size property")
        void shouldBindMaxFileSize() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.max-file-size=20MB")
                    .run(context -> {
                        CompilerProperties props = context.getBean(CompilerProperties.class);
                        assertThat(props.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(20));
                    });
        }

        @Test
        @DisplayName("should bind pretty-print property")
        void shouldBindPrettyPrint() {
            contextRunner
                    .withPropertyValues("sdlc.compiler.pretty-print=true")
                    .run(context -> {
                        CompilerProperties props = context.getBean(CompilerProperties.class);
                        assertThat(props.isPrettyPrint()).isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("SerializationFormat enum")
    class SerializationFormatEnum {

        @Test
        @DisplayName("should have JSON value")
        void shouldHaveJsonValue() {
            assertThat(CompilerProperties.SerializationFormat.JSON)
                    .isNotNull();
        }

        @Test
        @DisplayName("should have YAML value")
        void shouldHaveYamlValue() {
            assertThat(CompilerProperties.SerializationFormat.YAML)
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("should allow setting strictMode")
        void shouldAllowSettingStrictMode() {
            CompilerProperties props = new CompilerProperties();
            props.setStrictMode(true);
            assertThat(props.isStrictMode()).isTrue();
        }

        @Test
        @DisplayName("should allow setting defaultSerializationFormat")
        void shouldAllowSettingDefaultSerializationFormat() {
            CompilerProperties props = new CompilerProperties();
            props.setDefaultSerializationFormat(CompilerProperties.SerializationFormat.YAML);
            assertThat(props.getDefaultSerializationFormat())
                    .isEqualTo(CompilerProperties.SerializationFormat.YAML);
        }

        @Test
        @DisplayName("should allow setting cacheEnabled")
        void shouldAllowSettingCacheEnabled() {
            CompilerProperties props = new CompilerProperties();
            props.setCacheEnabled(false);
            assertThat(props.isCacheEnabled()).isFalse();
        }

        @Test
        @DisplayName("should allow setting maxFileSize")
        void shouldAllowSettingMaxFileSize() {
            CompilerProperties props = new CompilerProperties();
            props.setMaxFileSize(DataSize.ofMegabytes(50));
            assertThat(props.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(50));
        }

        @Test
        @DisplayName("should allow setting prettyPrint")
        void shouldAllowSettingPrettyPrint() {
            CompilerProperties props = new CompilerProperties();
            props.setPrettyPrint(true);
            assertThat(props.isPrettyPrint()).isTrue();
        }
    }
}
