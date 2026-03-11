package ru.hgd.sdlc.compiler.testing;

/**
 * Factory for sample Markdown strings used in parsing tests.
 * Provides various markdown samples for testing parser behavior.
 */
public final class MarkdownFixtures implements TestFixture {

    private MarkdownFixtures() {
        // Utility class - no instantiation
    }

    /**
     * Returns a valid minimal flow markdown string.
     */
    public static String validFlowMarkdown() {
        return """
            ---
            id: simple-flow
            name: Simple Flow
            version: 1.0.0
            startRoles:
              - developer
            ---

            # Simple Flow

            A simple flow for testing purposes.

            ## Phases

            ### Main Phase

            This is the main execution phase.

            #### Nodes

            - **execute**: Runs the main task
            """;
    }

    /**
     * Returns a valid multi-phase flow markdown string.
     */
    public static String multiPhaseFlowMarkdown() {
        return """
            ---
            id: multi-phase-flow
            name: Multi-Phase Flow
            version: 1.0.0
            description: A flow with multiple phases
            startRoles:
              - developer
              - tech_lead
            phases:
              - id: development
                name: Development
                order: 0
              - id: review
                name: Code Review
                order: 1
              - id: deployment
                name: Deployment
                order: 2
            ---

            # Multi-Phase Flow

            This flow demonstrates multiple phases.

            ## Development Phase

            The initial development phase where code is written.

            ## Review Phase

            Code review and quality checks.

            ## Deployment Phase

            Final deployment to production.
            """;
    }

    /**
     * Returns a valid skill markdown string.
     */
    public static String validSkillMarkdown() {
        return """
            ---
            id: code-generator
            name: Code Generator
            version: 1.0.0
            handler: skill://code-generator
            tags:
              - ai
              - code-gen
            inputSchema:
              type: object
              properties:
                language:
                  type: string
                requirements:
                  type: string
            ---

            # Code Generator Skill

            This skill generates code based on requirements.

            ## Usage

            Provide the target language and requirements description.
            """;
    }

    /**
     * Returns a skill markdown with parameters.
     */
    public static String skillWithParametersMarkdown() {
        return """
            ---
            id: parameterized-skill
            name: Parameterized Skill
            version: 2.0.0
            handler: builtin://process
            inputSchema:
              type: object
              properties:
                inputPath:
                  type: string
                  description: Path to input file
                options:
                  type: object
                  properties:
                    verbose:
                      type: boolean
                    timeout:
                      type: integer
              required:
                - inputPath
            outputSchema:
              type: object
              properties:
                status:
                  type: string
                outputPath:
                  type: string
            ---

            # Parameterized Skill

            A skill with detailed input and output schemas.
            """;
    }

    /**
     * Returns markdown with invalid frontmatter (malformed YAML).
     */
    public static String invalidFrontmatterMarkdown() {
        return """
            ---
            id: broken-flow
            name: Broken Flow
            version: [invalid version format]
            startRoles:
              - developer
              - [unclosed array
            ---

            # Broken Flow

            This flow has invalid YAML frontmatter.
            """;
    }

    /**
     * Returns markdown with missing required fields.
     */
    public static String missingRequiredFieldsMarkdown() {
        return """
            ---
            name: Flow Without ID
            version: 1.0.0
            ---

            # Flow Without ID

            This flow is missing the required `id` field.
            """;
    }

    /**
     * Returns markdown with unclosed frontmatter.
     */
    public static String unclosedFrontmatterMarkdown() {
        return """
            ---
            id: unclosed-flow
            name: Unclosed Flow
            version: 1.0.0

            # Unclosed Flow

            This flow is missing the closing frontmatter delimiter.
            """;
    }

    /**
     * Returns markdown with unknown/extra fields.
     */
    public static String unknownFieldsMarkdown() {
        return """
            ---
            id: flow-with-extras
            name: Flow With Extras
            version: 1.0.0
            unknownField: this should trigger a warning
            anotherUnknown: 123
            startRoles:
              - developer
            ---

            # Flow With Unknown Fields

            This flow has fields that are not recognized.
            """;
    }

    /**
     * Returns a flow markdown with gates.
     */
    public static String flowWithGatesMarkdown() {
        return """
            ---
            id: flow-with-gates
            name: Flow With Gates
            version: 1.0.0
            phases:
              - id: planning
                name: Planning
                order: 0
                gates:
                  - id: approval
                    type: APPROVAL
                    requiredApprovers:
                      - tech_lead
                      - product_owner
              - id: execution
                name: Execution
                order: 1
            ---

            # Flow With Gates

            This flow demonstrates approval gates.

            ## Planning Phase

            Create a plan and get approval.

            ## Execution Phase

            Execute the approved plan.
            """;
    }

    /**
     * Returns a complex flow markdown with all features.
     */
    public static String complexFlowMarkdown() {
        return """
            ---
            id: complex-flow
            name: Complex Flow
            version: 2.0.0
            description: A complex flow demonstrating all features
            startRoles:
              - developer
              - tech_lead
            resumePolicy: FROM_CHECKPOINT
            phases:
              - id: init
                name: Initialization
                order: 0
              - id: develop
                name: Development
                order: 1
              - id: review
                name: Review
                order: 2
              - id: deploy
                name: Deployment
                order: 3
            artifacts:
              - id: source-code
                name: Source Code
                required: true
              - id: test-results
                name: Test Results
                required: false
            ---

            # Complex Flow

            A comprehensive flow demonstrating all available features.

            ## Initialization

            Set up the environment and prepare for development.

            ## Development

            Implement the required features.

            ## Review

            Review the implementation and approve for deployment.

            ## Deployment

            Deploy to the target environment.

            ### Instructions

            1. Verify all tests pass
            2. Check code coverage
            3. Deploy to staging first
            4. Run smoke tests
            5. Deploy to production
            """;
    }

    /**
     * Returns empty markdown (edge case).
     */
    public static String emptyMarkdown() {
        return "";
    }

    /**
     * Returns markdown with only whitespace (edge case).
     */
    public static String whitespaceOnlyMarkdown() {
        return "   \n\t\n   ";
    }

    /**
     * Returns markdown without any frontmatter.
     */
    public static String noFrontmatterMarkdown() {
        return """
            # Plain Markdown

            This is just regular markdown without any YAML frontmatter.
            It should fail parsing.
            """;
    }
}
