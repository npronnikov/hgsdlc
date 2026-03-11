package com.example.sdlc.compiler.domain.model;

import com.example.sdlc.shared.hashing.Sha256;
import java.util.List;
import java.util.Map;

/**
 * Compiled Intermediate Representation of a Flow.
 * This is what the runtime executes - never raw Markdown.
 */
public record FlowIr(
    String flowId,
    String flowVersion,
    Sha256 contentHash,
    List<StageIr> stages,
    Map<String, NodeIr> nodeIndex,
    Map<String, Object> compiledConfig
) {
    public NodeIr getNode(String nodeId) {
        return nodeIndex.get(nodeId);
    }

    public record StageIr(
        String stageId,
        String name,
        int order,
        List<String> nodeIds,
        List<String> gateIds
    ) {}

    public record NodeIr(
        String nodeId,
        String type,
        String handler,
        Map<String, Object> config,
        List<String> dependencies
    ) {}
}
