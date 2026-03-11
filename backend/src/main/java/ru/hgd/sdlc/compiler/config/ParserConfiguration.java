package ru.hgd.sdlc.compiler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;

/**
 * Configuration for parser beans in the compiler module.
 * Provides frontmatter extraction and document parsing capabilities.
 */
@Configuration
public class ParserConfiguration {

    /**
     * Creates the FrontmatterExtractor bean for extracting YAML frontmatter from Markdown.
     *
     * @return a new FrontmatterExtractor instance
     */
    @Bean
    public FrontmatterExtractor frontmatterExtractor() {
        return new FrontmatterExtractor();
    }

    /**
     * Creates the FlowParser bean for parsing FlowDocument from Markdown.
     *
     * @return a new FlowParser instance
     */
    @Bean
    public FlowParser flowParser() {
        return new FlowParser();
    }

    /**
     * Creates the SkillParser bean for parsing SkillDocument from Markdown.
     *
     * @return a new SkillParser instance
     */
    @Bean
    public SkillParser skillParser() {
        return new SkillParser();
    }
}
