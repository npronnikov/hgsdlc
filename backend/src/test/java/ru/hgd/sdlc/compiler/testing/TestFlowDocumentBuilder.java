package ru.hgd.sdlc.compiler.testing;

import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactBinding;
import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateId;
import ru.hgd.sdlc.compiler.domain.model.authored.ExecutorKind;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.GateKind;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeType;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.Role;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for creating custom FlowDocument instances in tests.
 * Provides a convenient API for building flows step by step.
 *
 * <p>Example usage:
 * <pre>{@code
 * FlowDocument flow = TestFlowDocumentBuilder.create("my-flow")
 *     .withName("My Custom Flow")
 *     .withPhase("dev", "Development")
 *     .withPhase("test", "Testing")
 *     .withExecutorStep("dev", "code", "Write Code", HandlerRef.builtin("code-gen"))
 *     .withExecutorStep("test", "run-tests", "Run Tests", HandlerRef.builtin("test"))
 *     .withGate("test", "approval", GateKind.APPROVAL, Set.of(Role.of("tech_lead")))
 *     .withTransition("code", "run-tests")
 *     .withTransition("run-tests", "approval")
 *     .build();
 * }</pre>
 */
public final class TestFlowDocumentBuilder implements TestFixture {

    private final FlowId id;
    private String name;
    private SemanticVersion version = SemanticVersion.of("1.0.0");
    private MarkdownBody description = MarkdownBody.of("");
    private final Map<PhaseId, PhaseBuilder> phases = new LinkedHashMap<>();
    private final Map<NodeId, NodeBuilder> nodes = new LinkedHashMap<>();
    private final Set<Role> startRoles = new HashSet<>();
    private final List<Transition> transitions = new ArrayList<>();

    private TestFlowDocumentBuilder(FlowId id) {
        this.id = id;
        this.name = id.value();
    }

    /**
     * Creates a new builder with the given flow ID.
     *
     * @param flowId the flow identifier
     * @return a new builder instance
     */
    public static TestFlowDocumentBuilder create(String flowId) {
        return new TestFlowDocumentBuilder(FlowId.of(flowId));
    }

    /**
     * Creates a new builder with the given flow ID.
     *
     * @param flowId the flow identifier
     * @return a new builder instance
     */
    public static TestFlowDocumentBuilder create(FlowId flowId) {
        return new TestFlowDocumentBuilder(flowId);
    }

    /**
     * Sets the flow name.
     *
     * @param name the flow name
     * @return this builder
     */
    public TestFlowDocumentBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the flow version.
     *
     * @param version the semantic version
     * @return this builder
     */
    public TestFlowDocumentBuilder withVersion(String version) {
        this.version = SemanticVersion.of(version);
        return this;
    }

    /**
     * Sets the flow version.
     *
     * @param version the semantic version
     * @return this builder
     */
    public TestFlowDocumentBuilder withVersion(SemanticVersion version) {
        this.version = version;
        return this;
    }

    /**
     * Sets the flow description.
     *
     * @param description the description markdown
     * @return this builder
     */
    public TestFlowDocumentBuilder withDescription(String description) {
        this.description = MarkdownBody.of(description);
        return this;
    }

    /**
     * Adds a phase to the flow.
     *
     * @param phaseId the phase identifier
     * @param name the phase name
     * @return this builder
     */
    public TestFlowDocumentBuilder withPhase(String phaseId, String name) {
        return withPhase(PhaseId.of(phaseId), name);
    }

    /**
     * Adds a phase to the flow.
     *
     * @param phaseId the phase identifier
     * @param name the phase name
     * @return this builder
     */
    public TestFlowDocumentBuilder withPhase(PhaseId phaseId, String name) {
        phases.put(phaseId, new PhaseBuilder(phaseId, name, phases.size()));
        return this;
    }

    /**
     * Adds an executor step to a phase.
     *
     * @param phaseId the phase to add the step to
     * @param stepId the step identifier
     * @param name the step name
     * @param handler the handler reference
     * @return this builder
     */
    public TestFlowDocumentBuilder withExecutorStep(String phaseId, String stepId, String name, HandlerRef handler) {
        return withExecutorStep(PhaseId.of(phaseId), NodeId.of(stepId), name, handler, ExecutorKind.SKILL);
    }

    /**
     * Adds an executor step to a phase with explicit executor kind.
     *
     * @param phaseId the phase to add the step to
     * @param stepId the step identifier
     * @param name the step name
     * @param handler the handler reference
     * @param executorKind the executor kind
     * @return this builder
     */
    public TestFlowDocumentBuilder withExecutorStep(String phaseId, String stepId, String name,
                                                     HandlerRef handler, ExecutorKind executorKind) {
        return withExecutorStep(PhaseId.of(phaseId), NodeId.of(stepId), name, handler, executorKind);
    }

    /**
     * Adds an executor step to a phase.
     *
     * @param phaseId the phase to add the step to
     * @param stepId the step identifier
     * @param name the step name
     * @param handler the handler reference
     * @param executorKind the executor kind
     * @return this builder
     */
    public TestFlowDocumentBuilder withExecutorStep(PhaseId phaseId, NodeId stepId, String name,
                                                     HandlerRef handler, ExecutorKind executorKind) {
        PhaseBuilder phase = phases.get(phaseId);
        if (phase == null) {
            throw new IllegalArgumentException("Phase not found: " + phaseId);
        }

        NodeBuilder node = new NodeBuilder(stepId, NodeType.EXECUTOR)
            .withName(name)
            .withPhaseId(phaseId)
            .withExecutorKind(executorKind)
            .withHandler(handler);

        nodes.put(stepId, node);
        phase.addStep(stepId);
        return this;
    }

    /**
     * Adds a gate to a phase.
     *
     * @param phaseId the phase to add the gate to
     * @param gateId the gate identifier
     * @param gateKind the gate kind
     * @param requiredApprovers the required approver roles
     * @return this builder
     */
    public TestFlowDocumentBuilder withGate(String phaseId, String gateId, GateKind gateKind,
                                             Set<Role> requiredApprovers) {
        return withGate(PhaseId.of(phaseId), NodeId.of(gateId), gateKind, requiredApprovers);
    }

    /**
     * Adds a gate to a phase.
     *
     * @param phaseId the phase to add the gate to
     * @param gateId the gate identifier
     * @param gateKind the gate kind
     * @param requiredApprovers the required approver roles
     * @return this builder
     */
    public TestFlowDocumentBuilder withGate(PhaseId phaseId, NodeId gateId, GateKind gateKind,
                                             Set<Role> requiredApprovers) {
        PhaseBuilder phase = phases.get(phaseId);
        if (phase == null) {
            throw new IllegalArgumentException("Phase not found: " + phaseId);
        }

        NodeBuilder node = new NodeBuilder(gateId, NodeType.GATE)
            .withName(gateId.value())
            .withPhaseId(phaseId)
            .withGateKind(gateKind)
            .withRequiredApprovers(requiredApprovers);

        nodes.put(gateId, node);
        phase.addGate(gateId);
        return this;
    }

    /**
     * Adds a transition between two nodes.
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @return this builder
     */
    public TestFlowDocumentBuilder withTransition(String fromId, String toId) {
        return withTransition(NodeId.of(fromId), NodeId.of(toId));
    }

    /**
     * Adds a transition between two nodes.
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @return this builder
     */
    public TestFlowDocumentBuilder withTransition(NodeId fromId, NodeId toId) {
        transitions.add(Transition.forward(fromId, toId));
        return this;
    }

    /**
     * Adds a conditional transition between two nodes.
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @param condition the condition expression
     * @return this builder
     */
    public TestFlowDocumentBuilder withTransition(String fromId, String toId, String condition) {
        return withTransition(NodeId.of(fromId), NodeId.of(toId), condition);
    }

    /**
     * Adds a conditional transition between two nodes.
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @param condition the condition expression
     * @return this builder
     */
    public TestFlowDocumentBuilder withTransition(NodeId fromId, NodeId toId, String condition) {
        transitions.add(Transition.forward(fromId, toId, condition));
        return this;
    }

    /**
     * Adds a rework transition (backward edge).
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @return this builder
     */
    public TestFlowDocumentBuilder withReworkTransition(String fromId, String toId) {
        transitions.add(Transition.rework(NodeId.of(fromId), NodeId.of(toId)));
        return this;
    }

    /**
     * Adds a skip transition.
     *
     * @param fromId the source node ID
     * @param toId the target node ID
     * @param condition the skip condition
     * @return this builder
     */
    public TestFlowDocumentBuilder withSkipTransition(String fromId, String toId, String condition) {
        transitions.add(Transition.skip(NodeId.of(fromId), NodeId.of(toId), condition));
        return this;
    }

    /**
     * Adds a start role.
     *
     * @param role the role that can start this flow
     * @return this builder
     */
    public TestFlowDocumentBuilder withStartRole(String role) {
        startRoles.add(Role.of(role));
        return this;
    }

    /**
     * Adds start roles.
     *
     * @param roles the roles that can start this flow
     * @return this builder
     */
    public TestFlowDocumentBuilder withStartRoles(Set<Role> roles) {
        startRoles.addAll(roles);
        return this;
    }

    /**
     * Adds input artifacts to a node.
     *
     * @param nodeId the node ID
     * @param artifactIds the artifact template IDs
     * @return this builder
     */
    public TestFlowDocumentBuilder withInputs(String nodeId, String... artifactIds) {
        NodeBuilder node = nodes.get(NodeId.of(nodeId));
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        for (String artifactId : artifactIds) {
            node.addInput(ArtifactBinding.required(ArtifactTemplateId.of(artifactId)));
        }
        return this;
    }

    /**
     * Adds output artifacts to a node.
     *
     * @param nodeId the node ID
     * @param artifactIds the artifact template IDs
     * @return this builder
     */
    public TestFlowDocumentBuilder withOutputs(String nodeId, String... artifactIds) {
        NodeBuilder node = nodes.get(NodeId.of(nodeId));
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        for (String artifactId : artifactIds) {
            node.addOutput(ArtifactBinding.required(ArtifactTemplateId.of(artifactId)));
        }
        return this;
    }

    /**
     * Sets instructions on a node.
     *
     * @param nodeId the node ID
     * @param instructions the instruction markdown
     * @return this builder
     */
    public TestFlowDocumentBuilder withInstructions(String nodeId, String instructions) {
        NodeBuilder node = nodes.get(NodeId.of(nodeId));
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        node.withInstructions(MarkdownBody.of(instructions));
        return this;
    }

    /**
     * Builds the FlowDocument.
     *
     * @return the constructed flow document
     */
    public FlowDocument build() {
        // Assign transitions to nodes
        for (Transition transition : transitions) {
            NodeBuilder fromNode = nodes.get(transition.from());
            if (fromNode != null) {
                fromNode.addTransition(transition);
            }
        }

        // Build phases map
        Map<PhaseId, PhaseDocument> phaseMap = new HashMap<>();
        for (PhaseBuilder pb : phases.values()) {
            phaseMap.put(pb.id, pb.build());
        }

        // Build nodes map
        Map<NodeId, NodeDocument> nodeMap = new HashMap<>();
        for (NodeBuilder nb : nodes.values()) {
            nodeMap.put(nb.id, nb.build());
        }

        // Build phase order
        List<PhaseId> phaseOrder = new ArrayList<>(phases.keySet());

        return FlowDocument.builder()
            .id(id)
            .name(name)
            .version(version)
            .description(description)
            .phaseOrder(phaseOrder)
            .phases(phaseMap)
            .nodes(nodeMap)
            .startRoles(startRoles.isEmpty() ? Set.of(Role.of("developer")) : startRoles)
            .build();
    }

    // Internal helper classes

    private static class PhaseBuilder {
        private final PhaseId id;
        private final String name;
        private final int order;
        private final List<NodeId> steps = new ArrayList<>();
        private final List<NodeId> gates = new ArrayList<>();

        PhaseBuilder(PhaseId id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }

        void addStep(NodeId nodeId) {
            steps.add(nodeId);
        }

        void addGate(NodeId nodeId) {
            gates.add(nodeId);
        }

        PhaseDocument build() {
            return PhaseDocument.builder()
                .id(id)
                .name(name)
                .order(order)
                .nodeOrder(steps)
                .gateOrder(gates)
                .build();
        }
    }

    private static class NodeBuilder {
        private final NodeId id;
        private final NodeType type;
        private String name;
        private PhaseId phaseId;
        private ExecutorKind executorKind;
        private HandlerRef handler;
        private GateKind gateKind;
        private Set<Role> requiredApprovers = Set.of();
        private MarkdownBody instructions;
        private final List<ArtifactBinding> inputs = new ArrayList<>();
        private final List<ArtifactBinding> outputs = new ArrayList<>();
        private final List<Transition> transitions = new ArrayList<>();

        NodeBuilder(NodeId id, NodeType type) {
            this.id = id;
            this.type = type;
        }

        NodeBuilder withName(String name) {
            this.name = name;
            return this;
        }

        NodeBuilder withPhaseId(PhaseId phaseId) {
            this.phaseId = phaseId;
            return this;
        }

        NodeBuilder withExecutorKind(ExecutorKind executorKind) {
            this.executorKind = executorKind;
            return this;
        }

        NodeBuilder withHandler(HandlerRef handler) {
            this.handler = handler;
            return this;
        }

        NodeBuilder withGateKind(GateKind gateKind) {
            this.gateKind = gateKind;
            return this;
        }

        NodeBuilder withRequiredApprovers(Set<Role> requiredApprovers) {
            this.requiredApprovers = new LinkedHashSet<>(requiredApprovers);
            return this;
        }

        NodeBuilder withInstructions(MarkdownBody instructions) {
            this.instructions = instructions;
            return this;
        }

        void addInput(ArtifactBinding binding) {
            inputs.add(binding);
        }

        void addOutput(ArtifactBinding binding) {
            outputs.add(binding);
        }

        void addTransition(Transition transition) {
            transitions.add(transition);
        }

        NodeDocument build() {
            return NodeDocument.builder()
                .id(id)
                .type(type)
                .name(name)
                .phaseId(phaseId)
                .executorKind(executorKind)
                .handler(handler)
                .gateKind(gateKind)
                .requiredApprovers(requiredApprovers)
                .instructions(instructions)
                .inputs(inputs)
                .outputs(outputs)
                .transitions(transitions)
                .build();
        }
    }
}
