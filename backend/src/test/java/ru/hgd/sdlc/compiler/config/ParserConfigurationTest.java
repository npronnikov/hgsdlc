package ru.hgd.sdlc.compiler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;

import static org.assertj.core.api.Assertions.assertThat;

class ParserConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ParserConfiguration.class);

    @Nested
    @DisplayName("Bean creation")
    class BeanCreation {

        @Test
        @DisplayName("should create FrontmatterExtractor bean")
        void shouldCreateFrontmatterExtractorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(FrontmatterExtractor.class);
                assertThat(context.getBean(FrontmatterExtractor.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create FlowParser bean")
        void shouldCreateFlowParserBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(FlowParser.class);
                assertThat(context.getBean(FlowParser.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create SkillParser bean")
        void shouldCreateSkillParserBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SkillParser.class);
                assertThat(context.getBean(SkillParser.class))
                        .isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("Bean instances")
    class BeanInstances {

        @Test
        @DisplayName("should create new instances on each context start")
        void shouldCreateNewInstances() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(ParserConfiguration.class);

            // First context
            runner.run(context1 -> {
                FrontmatterExtractor extractor1 = context1.getBean(FrontmatterExtractor.class);

                // Second context (different instance)
                runner.run(context2 -> {
                    FrontmatterExtractor extractor2 = context2.getBean(FrontmatterExtractor.class);
                    assertThat(extractor1).isNotSameAs(extractor2);
                });
            });
        }
    }
}
