package com.izimi.aiplayermod.cortex.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskDAG {

    public record Dependency(String id, String type, double weight, List<Binding> bindings) {}
    public record Binding(String from, String to, String transform) {}
    public record SubtaskNode(String id, String name, String action, String target, int count,
                              List<Dependency> dependsOn, boolean isBottleneck) {}

    private final String taskId;
    private final List<SubtaskNode> nodes;
    private final Map<String, SubtaskNode> nodeIndex;

    public TaskDAG(String taskId, List<SubtaskNode> nodes) {
        this.taskId = taskId;
        this.nodes = new ArrayList<>(nodes);
        this.nodeIndex = new HashMap<>();
        for (SubtaskNode n : nodes) {
            nodeIndex.put(n.id(), n);
        }
    }

    public String taskId() { return taskId; }
    public List<SubtaskNode> nodes() { return nodes; }
    public SubtaskNode getNode(String id) { return nodeIndex.get(id); }

    public List<SubtaskNode> getReadyNodes(Map<String, Boolean> completed) {
        List<SubtaskNode> ready = new ArrayList<>();
        for (SubtaskNode n : nodes) {
            if (completed.getOrDefault(n.id(), false)) continue;
            boolean allDepsMet = true;
            for (Dependency dep : n.dependsOn()) {
                if (!completed.getOrDefault(dep.id(), false)) {
                    allDepsMet = false;
                    break;
                }
            }
            if (allDepsMet) ready.add(n);
        }
        return ready;
    }

    public List<String> traceUpstream(String nodeId) {
        SubtaskNode n = nodeIndex.get(nodeId);
        if (n == null) return List.of();
        List<String> upstream = new ArrayList<>();
        for (Dependency dep : n.dependsOn()) {
            upstream.add(dep.id());
            upstream.addAll(traceUpstream(dep.id()));
        }
        return upstream;
    }

    public static TaskDAG fromLLMJson(String taskId, List<Map<String, Object>> subtaskMaps) {
        List<SubtaskNode> nodes = new ArrayList<>();
        for (Map<String, Object> m : subtaskMaps) {
            String id = (String) m.get("id");
            String name = (String) m.getOrDefault("name", id);
            String action = (String) m.getOrDefault("action", "");
            String target = (String) m.getOrDefault("target", "");
            int count = ((Number) m.getOrDefault("count", 1)).intValue();
            boolean isBottleneck = Boolean.TRUE.equals(m.get("is_bottleneck"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> depMaps = (List<Map<String, Object>>) m.getOrDefault("depends_on", List.of());
            List<Dependency> deps = new ArrayList<>();
            for (Map<String, Object> d : depMaps) {
                String depId = (String) d.get("id");
                String depType = (String) d.getOrDefault("type", "hard");
                double weight = ((Number) d.getOrDefault("weight", 1.0)).doubleValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> bindMaps = (List<Map<String, Object>>) d.getOrDefault("bindings", List.of());
                List<Binding> bindings = new ArrayList<>();
                for (Map<String, Object> b : bindMaps) {
                    bindings.add(new Binding(
                            (String) b.get("from"),
                            (String) b.get("to"),
                            (String) b.get("transform")
                    ));
                }
                deps.add(new Dependency(depId, depType, weight, bindings));
            }
            nodes.add(new SubtaskNode(id, name, action, target, count, deps, isBottleneck));
        }
        return new TaskDAG(taskId, nodes);
    }

    public static class BottleneckDetector {
        public static List<String> detectStructural(List<SubtaskNode> nodes) {
            Map<String, Integer> inDegree = new HashMap<>();
            for (SubtaskNode n : nodes) {
                inDegree.putIfAbsent(n.id(), 0);
                for (Dependency dep : n.dependsOn()) {
                    inDegree.merge(dep.id(), 1, Integer::sum);
                }
            }
            List<String> bottlenecks = new ArrayList<>();
            for (var e : inDegree.entrySet()) {
                if (e.getValue() >= 3) bottlenecks.add(e.getKey());
            }
            return bottlenecks;
        }
    }
}
