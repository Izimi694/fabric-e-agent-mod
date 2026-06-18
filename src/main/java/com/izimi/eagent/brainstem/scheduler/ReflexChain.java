package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.planner.TaskDAG;
import com.izimi.eagent.cortex.planner.TaskDAG.SubtaskNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class ReflexChain {

    public static class ReflexNode {
        private final String id;
        private final String reflexId;
        private final Set<String> prev = new HashSet<>();
        private final Set<String> next = new HashSet<>();
        private boolean isBottleneck;
        private double baseWeight;
        private final Map<String, Double> taskConfidences = new HashMap<>();
        private int consecutiveFailures = 0;
        private int totalAttempts = 0;
        private double successRate = 0.5;

        public ReflexNode(String id, String reflexId, double baseWeight, boolean isBottleneck) {
            this.id = id;
            this.reflexId = reflexId;
            this.baseWeight = baseWeight;
            this.isBottleneck = isBottleneck;
        }

        public String id() { return id; }
        public String reflexId() { return reflexId; }
        public Set<String> prev() { return prev; }
        public Set<String> next() { return next; }
        public boolean isBottleneck() { return isBottleneck; }
        public void setBottleneck(boolean b) { isBottleneck = b; }
        public double baseWeight() { return baseWeight; }

        public double getSharedWeight(String taskId) {
            double taskConf = taskConfidences.getOrDefault(taskId, 0.5);
            return baseWeight * taskConf;
        }

        public void recordOutcome(boolean success, String taskId) {
            totalAttempts++;
            if (success) {
                consecutiveFailures = 0;
                taskConfidences.merge(taskId, 0.05, Double::sum);
            } else {
                consecutiveFailures++;
                taskConfidences.merge(taskId, -0.03, Double::sum);
            }
            taskConfidences.put(taskId, Math.max(0.01, Math.min(1.0, taskConfidences.get(taskId))));
            successRate = totalAttempts > 0 ? (successRate * (totalAttempts - 1) + (success ? 1 : 0)) / totalAttempts : 0.5;
        }

        public int getConsecutiveFailures() { return consecutiveFailures; }
        public double getSuccessRate() { return successRate; }
        public boolean isStable() {
            return totalAttempts >= 10 && consecutiveFailures == 0;
        }
    }

    private final String taskId;
    private final Map<String, ReflexNode> nodeMap = new LinkedHashMap<>();
    private final List<String> rootNodes = new ArrayList<>();
    private ReflexGraph reflexGraph;

    public ReflexChain(String taskId) {
        this.taskId = taskId;
    }

    public String taskId() { return taskId; }
    public void setReflexGraph(ReflexGraph graph) { this.reflexGraph = graph; }
    public ReflexGraph getReflexGraph() { return reflexGraph; }

    public ReflexNode addNode(String id, String reflexId, double baseWeight, boolean isBottleneck) {
        ReflexNode node = new ReflexNode(id, reflexId, baseWeight, isBottleneck);
        nodeMap.put(id, node);
        if (reflexGraph != null) {
            reflexGraph.addNode(reflexId, baseWeight, 0.3, 1);
        }
        return node;
    }

    public void link(String fromId, String toId) {
        ReflexNode from = nodeMap.get(fromId);
        ReflexNode to = nodeMap.get(toId);
        if (from != null && to != null) {
            from.next().add(toId);
            to.prev().add(fromId);
        }
        if (reflexGraph != null && from != null && to != null) {
            reflexGraph.addEdge(from.reflexId, to.reflexId, ReflexGraph.EdgeType.PRECEDES);
        }
    }

    public ReflexNode getNode(String id) { return nodeMap.get(id); }
    public Collection<ReflexNode> allNodes() { return nodeMap.values(); }

    public List<ReflexNode> getReadyNodes(Set<String> completed) {
        List<ReflexNode> ready = new ArrayList<>();
        for (ReflexNode n : nodeMap.values()) {
            if (completed.contains(n.id())) continue;
            if (completed.containsAll(n.prev())) {
                ready.add(n);
            }
        }
        return ready;
    }

    public ReflexNode getNextCandidate(Set<String> afterSkip, List<ReflexNode> candidates) {
        for (ReflexNode c : candidates) {
            if (!afterSkip.contains(c.id())) return c;
        }
        return null;
    }

    public List<String> traceUpstream(String nodeId) {
        if (reflexGraph != null) {
            return reflexGraph.getPredecessors(nodeId).stream().toList();
        }
        List<String> upstream = new ArrayList<>();
        ReflexNode n = nodeMap.get(nodeId);
        if (n == null) return upstream;
        for (String p : n.prev()) {
            upstream.add(p);
            upstream.addAll(traceUpstream(p));
        }
        return upstream;
    }

    public boolean onNodeResult(String nodeId, boolean success, String taskId) {
        ReflexNode n = nodeMap.get(nodeId);
        if (n == null) return false;
        n.recordOutcome(success, taskId);
        if (reflexGraph != null && n != null) {
            reflexGraph.recordEdgeOutcome(n.reflexId, n.id(), success, null);
        }
        return n.consecutiveFailures == 0;
    }

    public String selectAlternative(String failedNodeId) {
        if (reflexGraph == null) return null;
        ReflexNode failed = nodeMap.get(failedNodeId);
        if (failed == null) return null;

        Set<String> successors = reflexGraph.getSuccessors(failed.reflexId);
        for (String alt : successors) {
            var edge = reflexGraph.findAnyEdge(failed.reflexId, alt);
            if (edge != null && edge.type() == ReflexGraph.EdgeType.ALTERNATIVE) {
                if (nodeMap.containsKey(alt)) continue;
                double altWeight = reflexGraph.getDynamicWeight(failed.reflexId, alt, null);
                if (altWeight >= 0.5) {
                    ReflexNode altNode = addNode(failed.id() + "_alt", alt, altWeight, false);
                    for (String prevId : failed.prev()) {
                        link(prevId, altNode.id());
                    }
                    for (String nextId : failed.next()) {
                        link(altNode.id(), nextId);
                    }
                    LOGGER.info("[ReflexChain] alternative: {} → {} (weight={:.2f})",
                            failedNodeId, alt, altWeight);
                    return altNode.id();
                }
            }
        }
        return null;
    }

    public Set<String> getSkippableNodes(String completedNodeId) {
        Set<String> skippable = new HashSet<>();
        if (reflexGraph == null) return skippable;
        ReflexNode completed = nodeMap.get(completedNodeId);
        if (completed == null) return skippable;

        for (String nextId : completed.next()) {
            ReflexNode next = nodeMap.get(nextId);
            if (next == null) continue;
            Set<String> successors = reflexGraph.getSuccessors(next.reflexId);
            boolean hasAlternative = false;
            for (String succ : successors) {
                var edge = reflexGraph.findAnyEdge(next.reflexId, succ);
                if (edge != null && edge.type() == ReflexGraph.EdgeType.ALTERNATIVE) {
                    hasAlternative = true;
                    break;
                }
            }
            if (hasAlternative) {
                skippable.add(nextId);
            }
        }
        return skippable;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public static ReflexChain buildFromDAG(TaskDAG dag) {
        return buildFromDAG(dag, null);
    }

    public static ReflexChain buildFromDAG(TaskDAG dag, ReflexGraph globalGraph) {
        ReflexChain chain = new ReflexChain(dag.taskId());
        chain.setReflexGraph(globalGraph);
        for (SubtaskNode sn : dag.nodes()) {
            chain.addNode(sn.id(), sn.action(), 0.5, sn.isBottleneck());
        }
        for (SubtaskNode sn : dag.nodes()) {
            for (var dep : sn.dependsOn()) {
                chain.link(dep.id(), sn.id());
            }
        }
        for (ReflexNode n : chain.nodeMap.values()) {
            if (n.prev().isEmpty()) chain.rootNodes.add(n.id());
        }
        return chain;
    }

    public void autoDetectBottlenecks() {
        if (reflexGraph != null) {
            reflexGraph.autoDetectBottlenecks();
            for (ReflexNode n : nodeMap.values()) {
                var gn = reflexGraph.getNode(n.reflexId);
                if (gn != null) n.setBottleneck(gn.isBottleneck());
            }
            return;
        }
        Map<String, Integer> inDegree = new HashMap<>();
        for (ReflexNode n : nodeMap.values()) {
            inDegree.putIfAbsent(n.id(), 0);
            for (String p : n.prev()) {
                inDegree.merge(p, 1, Integer::sum);
            }
        }
        for (var e : inDegree.entrySet()) {
            ReflexNode n = nodeMap.get(e.getKey());
            if (n != null && e.getValue() >= 3) {
                n.setBottleneck(true);
            }
        }
    }
}
