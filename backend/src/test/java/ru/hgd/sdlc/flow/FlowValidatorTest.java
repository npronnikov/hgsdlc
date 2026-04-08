package ru.hgd.sdlc.flow;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.flow.application.FlowValidator;
import ru.hgd.sdlc.flow.domain.ExecutionContextEntry;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;

class FlowValidatorTest {

    private final FlowValidator validator = new FlowValidator();

    @Test
    void humanInputRequiresAtLeastOneModifiablePredecessorArtifact() {
        NodeModel producer = NodeModel.builder()
                .id("producer")
                .type("ai")
                .executionContext(List.of())
                .instruction("Generate questions")
                .onSuccess("input")
                .onFailure("end")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel input = NodeModel.builder()
                .id("input")
                .type("human_input")
                .instruction("Review and edit")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(false)
                                .nodeId("producer")
                                .build()
                ))
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).build()
                ))
                .onSubmit("end")
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("producer")
                .nodes(List.of(producer, input, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("execution_context artifact_ref with modifiable=true")));
    }

    @Test
    void validHumanInputWithModifiableArtifactPassesValidation() {
        NodeModel producer = NodeModel.builder()
                .id("producer")
                .type("ai")
                .executionContext(List.of())
                .instruction("Generate questions")
                .onSuccess("input")
                .onFailure("end")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel input = NodeModel.builder()
                .id("input")
                .type("human_input")
                .instruction("Review and edit")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(true)
                                .nodeId("producer")
                                .build()
                ))
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).build()
                ))
                .onSubmit("end")
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("producer")
                .nodes(List.of(producer, input, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
    }

    @Test
    void humanInputRunArtifactRefRequiresNodeId() {
        NodeModel producer = NodeModel.builder()
                .id("producer")
                .type("ai")
                .executionContext(List.of())
                .instruction("Generate questions")
                .onSuccess("input")
                .onFailure("end")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel input = NodeModel.builder()
                .id("input")
                .type("human_input")
                .instruction("Review and edit")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(true)
                                .nodeId("")
                                .build()
                ))
                .onSubmit("end")
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("producer")
                .nodes(List.of(producer, input, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("requires node_id")));
    }

    @Test
    void humanInputProducedArtifactsMustMatchModifiableUpstreamArtifacts() {
        NodeModel producer = NodeModel.builder()
                .id("producer")
                .type("ai")
                .executionContext(List.of())
                .instruction("Generate questions")
                .onSuccess("input")
                .onFailure("end")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel input = NodeModel.builder()
                .id("input")
                .type("human_input")
                .instruction("Review and edit")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(true)
                                .nodeId("producer")
                                .build()
                ))
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("answers.md").scope("run").required(true).build()
                ))
                .onSubmit("end")
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("producer")
                .nodes(List.of(producer, input, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("missing required artifact from execution_context modifiable set")));
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("extra artifact not found in execution_context modifiable set")));
    }

    @Test
    void humanInputProducedArtifactsDetectsUpstreamCollisions() {
        NodeModel producerA = NodeModel.builder()
                .id("producer-a")
                .type("ai")
                .executionContext(List.of())
                .instruction("Generate A")
                .onSuccess("input")
                .onFailure("producer-b")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel producerB = NodeModel.builder()
                .id("producer-b")
                .type("command")
                .executionContext(List.of())
                .instruction("Generate B")
                .onSuccess("input")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel input = NodeModel.builder()
                .id("input")
                .type("human_input")
                .instruction("Review and edit")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(true)
                                .nodeId("producer-a")
                                .build()
                        ,
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("questions.md")
                                .required(true)
                                .modifiable(true)
                                .nodeId("producer-b")
                                .build()
                ))
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("questions.md").scope("run").required(true).build()
                ))
                .onSubmit("end")
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("producer-a")
                .nodes(List.of(producerA, producerB, input, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("collision in execution_context modifiable artifacts")));
    }

    @Test
    void transferModeByValueIsAllowedOnlyForAiNodes() {
        NodeModel approval = NodeModel.builder()
                .id("approval")
                .type("human_approval")
                .executionContext(List.of(
                        ExecutionContextEntry.builder()
                                .type("artifact_ref")
                                .scope("run")
                                .path("input.md")
                                .required(true)
                                .nodeId("src")
                                .transferMode("by_value")
                                .build()
                ))
                .onApprove("end")
                .onRework(NodeModel.OnRework.builder().nextNode("src").build())
                .producedArtifacts(List.of())
                .expectedMutations(List.of())
                .build();
        NodeModel src = NodeModel.builder()
                .id("src")
                .type("ai")
                .executionContext(List.of())
                .instruction("generate")
                .onSuccess("approval")
                .onFailure("end")
                .producedArtifacts(List.of(
                        PathRequirement.builder().path("input.md").scope("run").required(true).modifiable(false).build()
                ))
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("src")
                .nodes(List.of(src, approval, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.stream().anyMatch((err) -> err.contains("supported only for ai nodes")));
    }

    @Test
    void reworkDiscardRequiresCheckpointEnabledTargetNode() {
        NodeModel executor = NodeModel.builder()
                .id("executor")
                .type("ai")
                .executionContext(List.of())
                .instruction("generate")
                .onSuccess("approval")
                .onFailure("end")
                .build();
        NodeModel approval = NodeModel.builder()
                .id("approval")
                .type("human_approval")
                .executionContext(List.of())
                .onApprove("end")
                .onRework(NodeModel.OnRework.builder().nextNode("end").build())
                .producedArtifacts(List.of())
                .expectedMutations(List.of())
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("executor")
                .nodes(List.of(executor, approval, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
    }

    @Test
    void reworkDiscardToCheckpointedCommandTargetPassesValidation() {
        NodeModel executor = NodeModel.builder()
                .id("executor")
                .type("command")
                .executionContext(List.of())
                .instruction("echo ok")
                .checkpointBeforeRun(true)
                .onSuccess("approval")
                .build();
        NodeModel approval = NodeModel.builder()
                .id("approval")
                .type("human_approval")
                .executionContext(List.of())
                .onApprove("end")
                .onRework(NodeModel.OnRework.builder().nextNode("executor").build())
                .producedArtifacts(List.of())
                .expectedMutations(List.of())
                .build();
        NodeModel end = NodeModel.builder()
                .id("end")
                .type("terminal")
                .executionContext(List.of())
                .build();
        FlowModel flow = FlowModel.builder()
                .id("f")
                .version("1.0")
                .canonicalName("f@1.0")
                .title("flow")
                .startNodeId("executor")
                .nodes(List.of(executor, approval, end))
                .build();

        List<String> errors = validator.validate(flow);
        Assertions.assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
    }
}
