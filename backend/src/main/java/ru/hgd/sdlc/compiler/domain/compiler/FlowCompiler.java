package ru.hgd.sdlc.compiler.domain.compiler;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.model.ir.*;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compiles FlowDocument to FlowIr.
 * Validates references, resolves dependencies, and builds the IR.
 *
 * <p>Per ADR-002: Runtime executes compiled IR, not Markdown.
 * The compiler transforms authored documents into executable IR.
 */
public class FlowCompiler {

    private static final String COMPILER_VERSION = "1.0.0";

    /**
     * Compiles a FlowDocument to FlowIr.
     *
     * @param document the source flow document
     * @return compilation result with IR or errors
     */
    public CompilerResult<FlowIr> compile(FlowDocument document) {
        Objects.requireNonNull(document, "document cannot be null");

        List<CompilerError> errors = new ArrayList<>();

        // Validate structure
        validateStructure(document, errors);

        // Validate phase order
        validatePhaseOrder(document, errors);

        // Validate nodes
        validateNodes(document, errors);

        // Validate transitions
        validateTransitions(document, errors);

        // Validate artifacts
        validateArtifacts(document, errors);

        if (!errors.isEmpty()) {
            return CompilerResult.failure(errors);
        }

        // Build IR
        try {
            FlowIr ir = buildIr(document);
            return CompilerResult.success(ir);
        } catch (Exception e) {
            return CompilerResult.failure(CompilerError.of(
                "E2999",
                "Unexpected compilation error: " + e.getMessage(),
                null,
                e
            ));
        }
    }

    private void validateStructure(FlowDocument document, List<CompilerError> errors) {
        // Check that all phases in phaseOrder exist
        for (PhaseId phaseId : document.phaseOrder()) {
            if (!document.phases().containsKey(phaseId)) {
                errors.add(CompilerError.invalidReference(
                    "phase",
                    phaseId.value(),
                    "phaseOrder"
                ));
            }
        }

        // Check that all nodes reference existing phases
        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            NodeDocument node = entry.getValue();
            if (node.phaseId().isPresent()) {
                PhaseId phaseId = node.phaseId().get();
                if (!document.phases().containsKey(phaseId)) {
                    errors.add(CompilerError.invalidReference(
                        "phase",
                        phaseId.value(),
                        "node[" + entry.getKey().value() + "]"
                    ));
                }
            }
        }
    }

    private void validatePhaseOrder(FlowDocument document, List<CompilerError> errors) {
        if (document.phaseOrder().isEmpty() && !document.phases().isEmpty()) {
            errors.add(CompilerError.invalidPhaseOrder(
                "phaseOrder is empty but phases are defined",
                "phaseOrder"
            ));
        }

        // Check for duplicate phases in order
        Set<PhaseId> seen = new HashSet<>();
        for (PhaseId phaseId : document.phaseOrder()) {
            if (!seen.add(phaseId)) {
                errors.add(CompilerError.invalidPhaseOrder(
                    "duplicate phase: " + phaseId.value(),
                    "phaseOrder"
                ));
            }
        }
    }

    private void validateNodes(FlowDocument document, List<CompilerError> errors) {
        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            NodeId nodeId = entry.getKey();
            NodeDocument node = entry.getValue();
            String location = "node[" + nodeId.value() + "]";

            // Validate executor nodes have handler
            if (node.isExecutor()) {
                if (node.handler().isEmpty()) {
                    errors.add(CompilerError.missingField("handler", location));
                }
                if (node.executorKind().isEmpty()) {
                    errors.add(CompilerError.missingField("executorKind", location));
                }
            }

            // Validate gate nodes have gateKind
            if (node.isGate()) {
                if (node.gateKind().isEmpty()) {
                    errors.add(CompilerError.missingField("gateKind", location));
                }
            }

            // Validate node belongs to a phase
            if (node.phaseId().isEmpty()) {
                errors.add(CompilerError.missingField("phaseId", location));
            }
        }
    }

    private void validateTransitions(FlowDocument document, List<CompilerError> errors) {
        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            NodeId nodeId = entry.getKey();
            NodeDocument node = entry.getValue();

            for (Transition transition : node.transitions()) {
                String location = "node[" + nodeId.value() + "].transitions";

                // Check target exists
                if (!document.nodes().containsKey(transition.to())) {
                    errors.add(CompilerError.invalidTransition(
                        nodeId.value(),
                        transition.to().value(),
                        "target node not found"
                    ));
                }

                // Check condition syntax (basic validation)
                if (transition.condition().isPresent() && transition.condition().get().isBlank()) {
                    errors.add(CompilerError.validationFailure(
                        "condition cannot be blank",
                        location
                    ));
                }
            }
        }
    }

    private void validateArtifacts(FlowDocument document, List<CompilerError> errors) {
        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            NodeId nodeId = entry.getKey();
            NodeDocument node = entry.getValue();
            String location = "node[" + nodeId.value() + "]";

            // Validate input bindings
            for (ArtifactBinding binding : node.inputs()) {
                if (!document.artifacts().containsKey(binding.artifactId().value())) {
                    errors.add(CompilerError.unresolvedArtifact(
                        binding.artifactId().value(),
                        location + ".inputs"
                    ));
                }
            }

            // Validate output bindings
            for (ArtifactBinding binding : node.outputs()) {
                if (!document.artifacts().containsKey(binding.artifactId().value())) {
                    errors.add(CompilerError.unresolvedArtifact(
                        binding.artifactId().value(),
                        location + ".outputs"
                    ));
                }
            }
        }
    }

    private FlowIr buildIr(FlowDocument document) {
        // Build phases
        List<PhaseIr> phases = buildPhases(document);

        // Build node index
        Map<NodeId, NodeIr> nodeIndex = new HashMap<>();
        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            nodeIndex.put(entry.getKey(), buildNodeIr(entry.getValue(), document));
        }

        // Build transitions
        List<TransitionIr> transitions = buildTransitions(document);

        // Build artifact contracts
        Map<ArtifactTemplateId, ArtifactContractIr> artifactContracts = buildArtifactContracts(document);

        // Build metadata
        IrMetadata metadata = buildMetadata(document);

        return FlowIr.builder()
            .flowId(document.id())
            .flowVersion(document.version())
            .metadata(metadata)
            .phases(phases)
            .nodeIndex(nodeIndex)
            .transitions(transitions)
            .artifactContracts(artifactContracts)
            .startRoles(document.startRoles())
            .resumePolicy(document.resumePolicy())
            .description(document.description())
            .build();
    }

    private List<PhaseIr> buildPhases(FlowDocument document) {
        return document.phaseOrder().stream()
            .map(phaseId -> {
                PhaseDocument phase = document.phases().get(phaseId);
                if (phase == null) {
                    return null;
                }
                return PhaseIr.builder()
                    .id(phase.id())
                    .name(phase.name())
                    .order(phase.order())
                    .nodeOrder(phase.nodeOrder())
                    .gateOrder(phase.gateOrder())
                    .description(phase.description())
                    .build();
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private NodeIr buildNodeIr(NodeDocument node, FlowDocument document) {
        Sha256 nodeHash = computeNodeHash(node);

        ExecutorConfig executorConfig = null;
        GateConfig gateConfig = null;

        if (node.isExecutor() && node.handler().isPresent()) {
            executorConfig = ExecutorConfig.builder()
                .kind(node.executorKind().orElse(ExecutorKind.SKILL))
                .handler(node.handler().get())
                .config(node.config())
                .build();
        }

        if (node.isGate() && node.gateKind().isPresent()) {
            gateConfig = GateConfig.builder()
                .kind(node.gateKind().get())
                .requiredApprovers(node.requiredApprovers())
                .config(node.config())
                .build();
        }

        List<ResolvedArtifactBinding> inputs = resolveBindings(node.inputs(), document);
        List<ResolvedArtifactBinding> outputs = resolveBindings(node.outputs(), document);

        return NodeIr.builder()
            .id(node.id())
            .type(node.type())
            .phaseId(node.phaseId().orElseThrow())
            .name(node.name())
            .executorConfig(executorConfig)
            .gateConfig(gateConfig)
            .inputs(inputs)
            .outputs(outputs)
            .instructions(node.instructions().orElse(null))
            .nodeHash(nodeHash)
            .build();
    }

    private List<ResolvedArtifactBinding> resolveBindings(List<ArtifactBinding> bindings, FlowDocument document) {
        return bindings.stream()
            .map(binding -> {
                ArtifactTemplateDocument template = document.artifacts().get(binding.artifactId().value());
                Sha256 schemaHash = template != null && template.schemaId() != null
                    ? Sha256.of(template.schemaId().value())
                    : Sha256.of("unknown");

                return ResolvedArtifactBinding.builder()
                    .artifactId(binding.artifactId())
                    .bindingName(binding.bindingName().orElse(null))
                    .required(binding.isRequired())
                    .schemaHash(schemaHash)
                    .build();
            })
            .toList();
    }

    private List<TransitionIr> buildTransitions(FlowDocument document) {
        List<TransitionIr> allTransitions = new ArrayList<>();
        int index = 0;

        for (Map.Entry<NodeId, NodeDocument> entry : document.nodes().entrySet()) {
            NodeId fromNode = entry.getKey();
            for (Transition transition : entry.getValue().transitions()) {
                allTransitions.add(TransitionIr.builder()
                    .fromNode(fromNode)
                    .toNode(transition.to())
                    .type(transition.type())
                    .condition(transition.condition().orElse(null))
                    .index(index++)
                    .build());
            }
        }

        return allTransitions;
    }

    private Map<ArtifactTemplateId, ArtifactContractIr> buildArtifactContracts(FlowDocument document) {
        return document.artifacts().entrySet().stream()
            .collect(Collectors.toMap(
                e -> ArtifactTemplateId.of(e.getKey()),
                e -> {
                    ArtifactTemplateDocument template = e.getValue();
                    return ArtifactContractIr.builder()
                        .id(ArtifactTemplateId.of(e.getKey()))
                        .logicalRole(template.logicalRole())
                        .schemaId(template.schemaId())
                        .required(template.required())
                        .schemaHash(Sha256.of(template.schemaId().value()))
                        .build();
                }
            ));
    }

    private IrMetadata buildMetadata(FlowDocument document) {
        Sha256 packageChecksum = computePackageChecksum(document);
        Sha256 irChecksum = computeIrChecksum(document);

        return IrMetadata.builder()
            .packageChecksum(packageChecksum)
            .irChecksum(irChecksum)
            .compiledAt(Instant.now())
            .compilerVersion(COMPILER_VERSION)
            .build();
    }

    private Sha256 computeNodeHash(NodeDocument node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.id().value());
        sb.append(node.type().name());
        sb.append(node.phaseId().map(PhaseId::value).orElse(""));
        sb.append(node.handler().map(h -> h.kind().name() + ":" + h.reference()).orElse(""));
        sb.append(node.gateKind().map(GateKind::name).orElse(""));

        return Sha256.of(sb.toString());
    }

    private Sha256 computePackageChecksum(FlowDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append(document.id().value());
        sb.append(document.version().toString());
        sb.append(document.phaseOrder().stream().map(PhaseId::value).collect(Collectors.joining(",")));
        return Sha256.of(sb.toString());
    }

    private Sha256 computeIrChecksum(FlowDocument document) {
        // This would be computed from the full serialized IR
        // For now, use a simple hash of document content
        StringBuilder sb = new StringBuilder();
        sb.append(document.id().value());
        sb.append("|");
        sb.append(document.version().toString());
        sb.append("|");
        sb.append(document.nodes().keySet().stream().map(NodeId::value).sorted().collect(Collectors.joining(",")));
        sb.append("|");
        sb.append(document.phaseOrder().stream().map(PhaseId::value).collect(Collectors.joining(",")));
        return Sha256.of(sb.toString());
    }
}
