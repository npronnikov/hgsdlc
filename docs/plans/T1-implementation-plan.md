# T1 Implementation Plan — Canonical Markdown Core + IR Compiler

## Overview

**Цель:** Создать canonical Markdown format и compiler pipeline, который превращает flow package в **deterministic executable IR**.

**Ключевой инвариант:** Runtime **никогда не интерпретирует Markdown напрямую**.

---

## Current State Analysis

### Уже существует

| Компонент | Файл | Статус |
|-----------|------|--------|
| FlowDocument (skeleton) | `compiler/domain/model/FlowDocument.java` | Базовые поля |
| PhaseDocument (skeleton) | `compiler/domain/model/PhaseDocument.java` | Базовые поля |
| NodeDocument (skeleton) | `compiler/domain/model/NodeDocument.java` | Базовые поля |
| ArtifactTemplateDocument (skeleton) | `compiler/domain/model/ArtifactTemplateDocument.java` | Базовые поля |
| SkillDocument (skeleton) | `compiler/domain/model/SkillDocument.java` | Базовые поля |
| FlowIr (skeleton) | `compiler/domain/model/FlowIr.java` | Базовая структура |
| Result<T,E> | `shared/kernel/Result.java` | Готов |
| DomainException | `shared/errors/DomainException.java` | Готов |
| Sha256 | `shared/hashing/Sha256.java` | Готов |

### Отсутствует

- Parser (frontmatter extraction, YAML parsing)
- Writer (round-trip safe serialization)
- Validator (graph validation, semantics)
- Compiler (Markdown → IR transformation)
- CLI commands
- Test fixtures
- IR JSON schema

---

## Proposed Package Structure

```
backend/src/main/java/com/example/sdlc/compiler/
├── domain/
│   ├── model/
│   │   ├── authored/                    # Markdown source models
│   │   │   ├── FlowDocument.java
│   │   │   ├── PhaseDocument.java
│   │   │   ├── NodeDocument.java
│   │   │   ├── ArtifactTemplateDocument.java
│   │   │   ├── SkillDocument.java
│   │   │   └── MarkdownBody.java        # NEW: preserves raw body bytes
│   │   │
│   │   └── ir/                          # Compiled IR models
│   │       ├── FlowIr.java
│   │       ├── PhaseIr.java             # NEW
│   │       ├── NodeIr.java              # NEW (extracted from inner record)
│   │       ├── TransitionIr.java        # NEW
│   │       ├── ArtifactContractIr.java  # NEW
│   │       └── IrMetadata.java          # NEW: version, checksums, timestamps
│   │
│   ├── parser/
│   │   ├── MarkdownParser.java          # NEW
│   │   ├── FrontmatterExtractor.java    # NEW
│   │   ├── FlowParser.java              # NEW
│   │   ├── SkillParser.java             # NEW
│   │   └── ParseError.java              # NEW
│   │
│   ├── writer/
│   │   ├── MarkdownWriter.java          # NEW
│   │   ├── CanonicalFormatter.java      # NEW
│   │   └── RoundTripVerifier.java       # NEW
│   │
│   ├── validator/
│   │   ├── FlowValidator.java           # NEW
│   │   ├── ValidationRule.java          # NEW
│   │   ├── ValidationResult.java        # NEW
│   │   ├── rules/
│   │   │   ├── NodeExistenceRule.java   # NEW
│   │   │   ├── TransitionValidityRule.java # NEW
│   │   │   ├── ArtifactResolutionRule.java # NEW
│   │   │   ├── NoCyclesRule.java        # NEW
│   │   │   └── BackwardEdgeRule.java    # NEW
│   │   └── ValidationError.java         # NEW
│   │
│   └── compiler/
│       ├── FlowCompiler.java            # NEW
│       ├── CompilationResult.java       # NEW
│       ├── IrNormalizer.java            # NEW
│       └── CompilationError.java        # NEW
│
├── application/
│   ├── CompileFlowService.java          # NEW
│   ├── ValidateFlowService.java         # NEW
│   └── InspectFlowService.java          # NEW
│
├── infrastructure/
│   ├── cli/
│   │   ├── CompilerCli.java             # NEW
│   │   └── commands/
│   │       ├── CompileCommand.java      # NEW
│   │       ├── ValidateCommand.java     # NEW
│   │       └── InspectCommand.java      # NEW
│   │
│   └── serialization/
│       ├── IrJsonSerializer.java        # NEW
│       └── IrJsonDeserializer.java      # NEW
│
└── CompilerConfig.java                  # NEW (Spring config)
```

---

## Domain Models Specification

### 1. Authored Markdown Models (domain/model/authored/)

#### FlowDocument

```java
public record FlowDocument(
    // Identity
    FlowId id,
    SemanticVersion version,

    // Structure
    List<PhaseId> phaseOrder,
    Map<PhaseId, PhaseDocument> phases,
    Map<NodeId, NodeDocument> nodes,

    // Execution hints
    Set<Role> startRoles,
    ResumePolicy resumePolicy,

    // Content
    MarkdownBody description,      // Preserved raw
    Map<String, ArtifactTemplateDocument> artifacts,

    // Metadata
    Instant authoredAt,
    String author
) {}
```

#### PhaseDocument

```java
public record PhaseDocument(
    PhaseId id,
    String name,
    MarkdownBody description,
    int order,
    List<NodeId> nodeOrder,
    List<NodeId> gateOrder
) {}
```

#### NodeDocument

```java
public record NodeDocument(
    NodeId id,
    NodeType type,                  // EXECUTOR | GATE

    // For EXECUTOR nodes
    ExecutorKind executorKind,      // SKILL | SCRIPT | BUILTIN
    SkillRef skill;                 // optional, for SKILL executor

    // For GATE nodes
    GateKind gateKind,              // APPROVAL | QUESTIONNAIRE | CONDITIONAL
    Set<Role> requiredApprovers;

    // Inputs/Outputs
    List<ArtifactBinding> inputs,
    List<ArtifactBinding> outputs,

    // Transitions
    List<Transition> transitions,

    // Content
    MarkdownBody instructions,
    MarkdownBody description,

    // Metadata
    Map<String, Object> config
) {}
```

#### ArtifactTemplateDocument

```java
public record ArtifactTemplateDocument(
    ArtifactTemplateId id,
    String name,
    LogicalRole logicalRole,
    SchemaId schemaId,
    PathPattern pathPattern,
    Set<PromotionEligibility> promotionEligibility,
    boolean required,
    Map<String, Object> constraints
) {}
```

#### SkillDocument

```java
public record SkillDocument(
    SkillId id,
    SemanticVersion version,
    String name,
    MarkdownBody description,
    HandlerRef handler,
    Schema inputSchema,
    Schema outputSchema,
    List<String> tags
) {}
```

#### MarkdownBody (NEW - Critical for round-trip)

```java
/**
 * Preserves raw markdown body bytes for byte-identical round-trip.
 */
public final class MarkdownBody {
    private final byte[] rawBytes;
    private final String content; // lazy, derived

    public static MarkdownBody of(byte[] rawBytes) { ... }
    public static MarkdownBody of(String content) { ... }

    public byte[] rawBytes() { return rawBytes.clone(); }
    public String content() { ... }

    // Critical: equals/hashCode based on byte content
}
```

---

### 2. Compiled IR Models (domain/model/ir/)

#### FlowIr

```java
public record FlowIr(
    // Identity
    FlowId flowId,
    SemanticVersion flowVersion,
    IrMetadata metadata,

    // Normalized graph
    List<PhaseIr> phases,
    Map<NodeId, NodeIr> nodeIndex,
    List<TransitionIr> transitions,

    // Artifact contracts
    Map<ArtifactTemplateId, ArtifactContractIr> artifactContracts,

    // Resolved references
    Map<SkillRef, Sha256> resolvedSkills,

    // Policy hooks
    List<PolicyHook> policyHooks,

    // Schema version for evolution
    int irSchemaVersion
) {}
```

#### NodeIr

```java
public record NodeIr(
    NodeId nodeId,
    NodeType type,
    PhaseId phaseId,

    // Normalized executor config
    ExecutorConfig executorConfig,

    // Normalized gate config
    GateConfig gateConfig,

    // Resolved artifact bindings
    List<ResolvedArtifactBinding> inputs,
    List<ResolvedArtifactBinding> outputs,

    // Transition indices (into FlowIr.transitions)
    List<Integer> transitionIndices,

    // Compiled instructions
    MarkdownBody compiledInstructions,

    // Deterministic hash of this node
    Sha256 nodeHash
) {}
```

#### TransitionIr

```java
public record TransitionIr(
    NodeId fromNode,
    NodeId toNode,
    TransitionCondition condition,
    TransitionType type            // FORWARD | REWORK | SKIP
) {}
```

#### ArtifactContractIr

```java
public record ArtifactContractIr(
    ArtifactTemplateId id,
    LogicalRole logicalRole,
    SchemaId schemaId,
    boolean required,
    Sha256 schemaHash
) {}
```

#### IrMetadata

```java
public record IrMetadata(
    Sha256 packageChecksum,        // Hash of all source files
    Sha256 irChecksum,             // Hash of this IR
    Instant compiledAt,
    String compilerVersion,
    int schemaVersion
) {}
```

---

## Parser Design

### Frontmatter Extraction Strategy

Markdown files follow this structure:

```markdown
---
id: my-flow
version: 1.0.0
phase_order: [setup, develop, review]
start_roles: [developer, architect]
---

# Flow Title

Body content here...
```

### Parser Pipeline

```
MarkdownFile (bytes)
    │
    ▼
FrontmatterExtractor
    │ extracts YAML frontmatter between --- delimiters
    │ preserves body bytes exactly
    ▼
ParsedMarkdown (frontmatter: Map, body: MarkdownBody)
    │
    ▼
FlowParser / SkillParser
    │ maps frontmatter to typed domain model
    │ validates required fields
    ▼
FlowDocument / SkillDocument
```

### Key Parser Classes

```java
public class FrontmatterExtractor {
    public Result<ParsedMarkdown, ParseError> extract(byte[] markdownBytes);
}

public record ParsedMarkdown(
    Map<String, Object> frontmatter,
    MarkdownBody body
) {}

public class FlowParser {
    public Result<FlowDocument, ParseError> parse(ParsedMarkdown parsed);
}
```

---

## Writer Design (Round-Trip Safe)

### Two Modes

1. **Preserve Mode:** Keep original formatting, only update frontmatter
2. **Canonical Mode:** Reformat everything to canonical style

```java
public class MarkdownWriter {
    public byte[] writePreserve(FlowDocument doc, byte[] originalBytes);
    public byte[] writeCanonical(FlowDocument doc);
}
```

### Round-Trip Invariant

```
parse(writePreserve(parse(original))) ≡ parse(original)
```

Body bytes must be **byte-identical** after round-trip.

---

## Validator Design

### Validation Rules

| Rule | Code | Description |
|------|------|-------------|
| NODE_EXISTENCE | E001 | All referenced node IDs exist |
| ARTIFACT_RESOLUTION | E002 | All inputs/outputs have artifact templates |
| TRANSITION_TARGET | E003 | All transition targets exist |
| TRANSITION_KIND | E004 | Executor/gate type matches transition expectations |
| NO_CYCLES | E005 | No cycles in forward graph |
| BACKWARD_EDGES | W001 | Backward edges only for rework |
| REQUIRED_FIELDS | E006 | All required frontmatter fields present |
| SKILL_RESOLUTION | E007 | All skill references resolvable |

### ValidationResult

```java
public record ValidationResult(
    boolean valid,
    List<ValidationError> errors,
    List<ValidationWarning> warnings
) {}
```

---

## Compiler Design

### Compilation Pipeline

```
FlowPackage (directory of .md files)
    │
    ▼
PackageReader
    │ reads all .md files
    │ parses each into domain models
    ▼
ParsedPackage (FlowDocument + NodeDocuments + SkillDocuments)
    │
    ▼
Validator
    │ applies all validation rules
    │ fails fast on errors
    ▼
ValidatedPackage
    │
    ▼
IrNormalizer
    │ builds normalized graph
    │ resolves all references
    │ computes checksums
    ▼
FlowIr
    │
    ▼
IrJsonSerializer
    │ serializes to deterministic JSON
    ▼
flow.ir.json
```

### Determinism Guarantees

1. **Stable ordering:** Lists sorted by ID
2. **Canonical JSON:** No whitespace, sorted keys
3. **Content-addressed:** IR checksum includes all content
4. **Reproducible:** Same input → same IR bytes

---

## Test Fixtures

### Location

```
backend/src/test/resources/fixtures/
├── flows/
│   ├── minimal/
│   │   └── flow.md                  # Minimal valid flow
│   ├── basic/
│   │   ├── flow.md
│   │   ├── phases/
│   │   │   ├── setup.md
│   │   │   ├── develop.md
│   │   │   └── review.md
│   │   └── skills/
│   │       └── initialize.md
│   ├── invalid/
│   │   ├── missing-id.md
│   │   ├── cyclic-transitions.md
│   │   └── unresolved-skill.md
│   └── round-trip/
│       └── preserve-test.md         # For round-trip tests
│
└── skills/
    ├── basic/
    │   └── code-review.md
    └── invalid/
        └── missing-handler.md
```

### Example Minimal Flow

```markdown
---
id: minimal-flow
version: 1.0.0
phase_order: [main]
start_roles: [developer]
---

# Minimal Flow

A minimal flow for testing.
```

---

## Implementation Slices

### Slice 1: Core Domain Models (2-3h)

**Files to create/modify:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/model/authored/FlowId.java` |
| CREATE | `compiler/domain/model/authored/PhaseId.java` |
| CREATE | `compiler/domain/model/authored/NodeId.java` |
| CREATE | `compiler/domain/model/authored/ArtifactTemplateId.java` |
| CREATE | `compiler/domain/model/authored/SkillId.java` |
| CREATE | `compiler/domain/model/authored/SchemaId.java` |
| CREATE | `compiler/domain/model/authored/MarkdownBody.java` |
| CREATE | `compiler/domain/model/authored/SemanticVersion.java` |
| CREATE | `compiler/domain/model/authored/NodeType.java` |
| CREATE | `compiler/domain/model/authored/ExecutorKind.java` |
| CREATE | `compiler/domain/model/authored/GateKind.java` |
| CREATE | `compiler/domain/model/authored/Role.java` |
| CREATE | `compiler/domain/model/authored/ResumePolicy.java` |
| CREATE | `compiler/domain/model/authored/Transition.java` |
| CREATE | `compiler/domain/model/authored/ArtifactBinding.java` |
| MODIFY | `compiler/domain/model/FlowDocument.java` → move to `authored/` |
| MODIFY | `compiler/domain/model/PhaseDocument.java` → move to `authored/` |
| MODIFY | `compiler/domain/model/NodeDocument.java` → move to `authored/` |
| MODIFY | `compiler/domain/model/ArtifactTemplateDocument.java` → move to `authored/` |
| MODIFY | `compiler/domain/model/SkillDocument.java` → move to `authored/` |

**Tests:**
- `FlowIdTest.java` - ID validation
- `SemanticVersionTest.java` - version parsing
- `MarkdownBodyTest.java` - byte preservation

---

### Slice 2: IR Domain Models (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/model/ir/FlowIr.java` |
| CREATE | `compiler/domain/model/ir/PhaseIr.java` |
| CREATE | `compiler/domain/model/ir/NodeIr.java` |
| CREATE | `compiler/domain/model/ir/TransitionIr.java` |
| CREATE | `compiler/domain/model/ir/ArtifactContractIr.java` |
| CREATE | `compiler/domain/model/ir/IrMetadata.java` |
| CREATE | `compiler/domain/model/ir/ExecutorConfig.java` |
| CREATE | `compiler/domain/model/ir/GateConfig.java` |
| CREATE | `compiler/domain/model/ir/ResolvedArtifactBinding.java` |
| CREATE | `compiler/domain/model/ir/PolicyHook.java` |
| CREATE | `compiler/domain/model/ir/TransitionType.java` |
| DELETE | `compiler/domain/model/FlowIr.java` (old location) |

**Tests:**
- `FlowIrTest.java` - IR construction
- `IrMetadataTest.java` - checksum computation

---

### Slice 3: Frontmatter Parser (2-3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/parser/FrontmatterExtractor.java` |
| CREATE | `compiler/domain/parser/ParsedMarkdown.java` |
| CREATE | `compiler/domain/parser/ParseError.java` |

**Dependencies:**
- Add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` to build.gradle.kts

**Tests:**
- `FrontmatterExtractorTest.java` - extraction, missing frontmatter, malformed YAML

---

### Slice 4: Flow Parser (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/parser/FlowParser.java` |
| CREATE | `compiler/domain/parser/PhaseParser.java` |
| CREATE | `compiler/domain/parser/NodeParser.java` |

**Tests:**
- `FlowParserTest.java` - valid flow, missing fields, invalid types
- Test fixtures: `fixtures/flows/minimal/`, `fixtures/flows/basic/`

---

### Slice 5: Skill Parser (1-2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/parser/SkillParser.java` |

**Tests:**
- `SkillParserTest.java`
- Test fixtures: `fixtures/skills/basic/`

---

### Slice 6: Markdown Writer (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/writer/MarkdownWriter.java` |
| CREATE | `compiler/domain/writer/CanonicalFormatter.java` |
| CREATE | `compiler/domain/writer/RoundTripVerifier.java` |

**Tests:**
- `MarkdownWriterPreserveTest.java` - body byte-identical
- `MarkdownWriterCanonicalTest.java` - canonical format
- `RoundTripTest.java` - parse → write → parse equivalence

---

### Slice 7: Validation Framework (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/validator/FlowValidator.java` |
| CREATE | `compiler/domain/validator/ValidationRule.java` |
| CREATE | `compiler/domain/validator/ValidationResult.java` |
| CREATE | `compiler/domain/validator/ValidationError.java` |
| CREATE | `compiler/domain/validator/ValidationWarning.java` |

**Tests:**
- `ValidationResultTest.java`

---

### Slice 8: Validation Rules (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/validator/rules/NodeExistenceRule.java` |
| CREATE | `compiler/domain/validator/rules/TransitionValidityRule.java` |
| CREATE | `compiler/domain/validator/rules/ArtifactResolutionRule.java` |
| CREATE | `compiler/domain/validator/rules/NoCyclesRule.java` |
| CREATE | `compiler/domain/validator/rules/BackwardEdgeRule.java` |
| CREATE | `compiler/domain/validator/rules/RequiredFieldsRule.java` |

**Tests:**
- `NodeExistenceRuleTest.java`
- `NoCyclesRuleTest.java`
- Test fixtures: `fixtures/flows/invalid/cyclic-transitions.md`

---

### Slice 9: IR Compiler (3-4h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/domain/compiler/FlowCompiler.java` |
| CREATE | `compiler/domain/compiler/CompilationResult.java` |
| CREATE | `compiler/domain/compiler/CompilationError.java` |
| CREATE | `compiler/domain/compiler/IrNormalizer.java` |
| CREATE | `compiler/domain/compiler/PackageReader.java` |

**Tests:**
- `FlowCompilerTest.java` - full compilation
- `IrDeterminismTest.java` - same input → same IR bytes

---

### Slice 10: IR Serialization (1-2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/infrastructure/serialization/IrJsonSerializer.java` |
| CREATE | `compiler/infrastructure/serialization/IrJsonDeserializer.java` |
| CREATE | `compiler/infrastructure/serialization/IrSchemaVersion.java` |

**Tests:**
- `IrJsonRoundTripTest.java` - serialize → deserialize → equals

---

### Slice 11: Application Services (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/application/CompileFlowService.java` |
| CREATE | `compiler/application/ValidateFlowService.java` |
| CREATE | `compiler/application/InspectFlowService.java` |

**Tests:**
- `CompileFlowServiceTest.java` (integration)

---

### Slice 12: CLI Commands (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/infrastructure/cli/CompilerCli.java` |
| CREATE | `compiler/infrastructure/cli/commands/CompileCommand.java` |
| CREATE | `compiler/infrastructure/cli/commands/ValidateCommand.java` |
| CREATE | `compiler/infrastructure/cli/commands/InspectCommand.java` |

**Dependencies:**
- Add `info.picocli:picocli:4.7.6` to build.gradle.kts

**Tests:**
- `CompileCommandTest.java`
- `ValidateCommandTest.java`

---

### Slice 13: Test Fixtures (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `fixtures/flows/minimal/flow.md` |
| CREATE | `fixtures/flows/basic/flow.md` |
| CREATE | `fixtures/flows/basic/phases/setup.md` |
| CREATE | `fixtures/flows/basic/phases/develop.md` |
| CREATE | `fixtures/flows/basic/phases/review.md` |
| CREATE | `fixtures/flows/basic/skills/initialize.md` |
| CREATE | `fixtures/flows/invalid/missing-id.md` |
| CREATE | `fixtures/flows/invalid/cyclic-transitions.md` |
| CREATE | `fixtures/flows/invalid/unresolved-skill.md` |
| CREATE | `fixtures/flows/round-trip/preserve-test.md` |
| CREATE | `fixtures/skills/basic/code-review.md` |
| CREATE | `fixtures/skills/invalid/missing-handler.md` |

---

### Slice 14: Spring Configuration (1h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `compiler/CompilerConfig.java` |

---

## Dependencies

### Add to build.gradle.kts

```kotlin
dependencies {
    // YAML parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")

    // CLI
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
}
```

---

## Definition of Done

### T1.1 Markdown data models
- [ ] All authored models (Flow, Phase, Node, ArtifactTemplate, Skill) defined
- [ ] MarkdownBody preserves raw bytes
- [ ] Value types for IDs (FlowId, NodeId, etc.)

### T1.2 Markdown parser
- [ ] FrontmatterExtractor extracts YAML + body
- [ ] FlowParser produces FlowDocument
- [ ] SkillParser produces SkillDocument
- [ ] Body preserved byte-identical

### T1.3 Writer (round-trip safe)
- [ ] Preserve mode keeps original formatting
- [ ] Canonical mode produces deterministic output
- [ ] Round-trip test: parse → write → parse ≡ original

### T1.4 Flow validator
- [ ] All validation rules implemented
- [ ] Cyclist detection works
- [ ] Backward edges validated as rework only

### T1.5 IR schema
- [ ] All IR models defined
- [ ] JSON serialization deterministic
- [ ] Schema version for evolution

### T1.6 IR compiler
- [ ] Compilation pipeline complete
- [ ] Same input → identical IR (determinism test)
- [ ] Checksums computed correctly

### T1.7 CLI
- [ ] `sdlc compile-flow <path>` works
- [ ] `sdlc validate-flow <path>` works
- [ ] `sdlc inspect-flow <ir-path>` works

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| YAML parsing edge cases | Extensive fixtures, strict mode |
| Round-trip corruption | Byte-level tests, MarkdownBody immutable |
| IR drift from source | Content checksums, deterministic compilation |
| Schema evolution | IrSchemaVersion field, versioned deserializers |

---

## Estimated Effort

| Slice | Hours |
|-------|-------|
| 1. Core Domain Models | 2-3 |
| 2. IR Domain Models | 2 |
| 3. Frontmatter Parser | 2-3 |
| 4. Flow Parser | 3 |
| 5. Skill Parser | 1-2 |
| 6. Markdown Writer | 2 |
| 7. Validation Framework | 2 |
| 8. Validation Rules | 3 |
| 9. IR Compiler | 3-4 |
| 10. IR Serialization | 1-2 |
| 11. Application Services | 2 |
| 12. CLI Commands | 2 |
| 13. Test Fixtures | 2 |
| 14. Spring Configuration | 1 |
| **Total** | **28-35 hours** |

---

## Next Steps After T1

Upon completion of T1, proceed to **T2: Registry + Release Pipeline + Provenance**:

- T2.1 Release builder (uses FlowCompiler)
- T2.2 Package structure
- T2.3 Registry service
- T2.4 Lock files
- T2.5 Provenance signing
