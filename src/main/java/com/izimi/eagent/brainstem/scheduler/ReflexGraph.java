package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.planner.TaskDAG;
import com.izimi.eagent.hippocampus.MemoryEdge;
import com.izimi.eagent.hormonal.NeuroState;
import com.izimi.eagent.util.JsonUtil;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ReflexGraph {

    public static final double DEFAULT_PRIOR_ALPHA = 1.0;
    public static final double DEFAULT_PRIOR_BETA = 1.0;
    public static final double CONSOLIDATION_THRESHOLD = 0.8;
    public static final double PRUNE_ACTIVATION_MIN = 3;
    public static final long PRUNE_IDLE_MS = 30 * 60 * 1000;
    public static final double CRITICAL_PATH_TICK_PER_ATOM = 20.0;

    // ── Edge types ──

    public enum EdgeType {
        PRECEDES,
        DEPENDS_ON,
        ALTERNATIVE,
        INHIBITS,
        COMPRESSES
    }

    // ── Node ──

    public static class ReflexGraphNode {
        String reflexId;
        String displayName;
        String category;
        double baseWeight;
        double proficiency;
        int atomCount;
        boolean isBottleneck;
        final Map<String, Double> taskConfidences = new HashMap<>();
        int totalAttempts;
        int consecutiveFailures;
        double successRate;
        long lastUsed;
        int usageCount;

        public ReflexGraphNode(String reflexId, double baseWeight, double proficiency, int atomCount) {
            this.reflexId = reflexId;
            this.baseWeight = baseWeight;
            this.proficiency = proficiency;
            this.atomCount = atomCount;
            this.displayName = reflexId;
            this.successRate = 0.5;
            this.lastUsed = System.currentTimeMillis();
        }

        public String reflexId() { return reflexId; }
        public String displayName() { return displayName; }
        public double baseWeight() { return baseWeight; }
        public double proficiency() { return proficiency; }
        public int atomCount() { return atomCount; }
        public boolean isBottleneck() { return isBottleneck; }
        public double successRate() { return successRate; }
        public int totalAttempts() { return totalAttempts; }
        public int usageCount() { return usageCount; }
        public long lastUsed() { return lastUsed; }
        public double hoursSinceLastUsed() { return (System.currentTimeMillis() - lastUsed) / 3600000.0; }

        public double getSharedWeight(String taskId) {
            double taskConf = taskConfidences.getOrDefault(taskId, 0.5);
            double decay = Math.exp(-0.1 * hoursSinceLastUsed() / 24.0);
            return baseWeight * taskConf * (0.7 + 0.3 * decay);
        }

        public String toCompactSummary() {
            String cat = category != null ? category : "uncategorized";
            return String.format("%s | cat=%s w=%.2f prof=%.2f sr=%.2f uses=%d",
                    reflexId, cat, baseWeight, proficiency, successRate, usageCount);
        }
    }

    // ── Edge ──

    public static class ReflexGraphEdge {
        final String fromId;
        final String toId;
        final EdgeType type;
        double priorAlpha;
        double priorBeta;
        int alphaSuccess;
        int betaFailure;
        int avgExecutionTicks;
        double compression;
        int activationCount;
        long lastActivated;
        double neSensitivity;
        double daSensitivity;
        boolean isPassive;
        boolean pruned;

        ReflexGraphEdge(String fromId, String toId, EdgeType type) {
            this(fromId, toId, type, DEFAULT_PRIOR_ALPHA, DEFAULT_PRIOR_BETA);
        }

        ReflexGraphEdge(String fromId, String toId, EdgeType type, double priorAlpha, double priorBeta) {
            this.fromId = fromId;
            this.toId = toId;
            this.type = type;
            this.priorAlpha = priorAlpha;
            this.priorBeta = priorBeta;
            this.compression = 1.0;
            this.neSensitivity = 1.0;
            this.daSensitivity = 1.0;
            this.lastActivated = System.currentTimeMillis();
        }

        public String fromId() { return fromId; }
        public String toId() { return toId; }
        public EdgeType type() { return type; }

        public double posteriorMean() {
            double a = priorAlpha + alphaSuccess;
            double b = priorBeta + betaFailure;
            return a / (a + b);
        }

        public double posteriorVariance() {
            double a = priorAlpha + alphaSuccess;
            double b = priorBeta + betaFailure;
            double n = a + b;
            return (a * b) / (n * n * (n + 1));
        }

        public boolean isConverged() {
            return posteriorVariance() < 1.0 / Math.E;
        }

        public double hormoneModulation(NeuroState state) {
            double mod = 1.0;
            if (type == EdgeType.DEPENDS_ON || type == EdgeType.PRECEDES) {
                mod *= (1.0 + state.ne() * neSensitivity);
            }
            if (state.ne() > 0.5 && type != EdgeType.INHIBITS) {
                mod *= 0.7;
            }
            if (state.da() > 0.5) {
                mod *= (1.0 + state.da() * daSensitivity * 0.3);
            }
            return Math.max(0.1, mod);
        }

        public double dynamicWeight(NeuroState state) {
            if (pruned) return 0.0;
            double riskGain = isPassive ? 1.0 : (1.0 - state.serotonin() * 0.2);
            return posteriorMean() * hormoneModulation(state) * compression * riskGain;
        }

        void recordActivation(boolean success, NeuroState state) {
            activationCount++;
            lastActivated = System.currentTimeMillis();
            double lr = 0.5 + state.da() * 1.5;
            if (success) {
                alphaSuccess += Math.max(1, (int) lr);
            } else {
                betaFailure += Math.max(1, (int) lr);
            }
        }

        void compress() {
            compression = Math.max(0.3, compression * 0.9);
        }
    }

    // ── Core state ──

    private final Map<String, ReflexGraphNode> nodes = new LinkedHashMap<>();
    private final List<ReflexGraphEdge> edges = new ArrayList<>();
    private transient Map<String, List<ReflexGraphEdge>> outEdges = new HashMap<>();
    private transient Map<String, List<ReflexGraphEdge>> inEdges = new HashMap<>();
    private transient boolean adjacencyDirty = true;

    // ── Node CRUD ──

    public ReflexGraphNode addNode(String reflexId, double baseWeight, double proficiency, int atomCount) {
        ReflexGraphNode n = new ReflexGraphNode(reflexId, baseWeight, proficiency, atomCount);
        nodes.put(reflexId, n);
        adjacencyDirty = true;
        return n;
    }

    public ReflexGraphNode getNode(String reflexId) {
        return nodes.get(reflexId);
    }

    public Collection<ReflexGraphNode> allNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int nodeCount() { return nodes.size(); }

    public void removeNode(String reflexId) {
        nodes.remove(reflexId);
        edges.removeIf(e -> e.fromId.equals(reflexId) || e.toId.equals(reflexId));
        adjacencyDirty = true;
    }

    // ── Edge CRUD ──

    public ReflexGraphEdge addEdge(String fromId, String toId, EdgeType type) {
        return addEdge(fromId, toId, type, DEFAULT_PRIOR_ALPHA, DEFAULT_PRIOR_BETA);
    }

    public ReflexGraphEdge addEdge(String fromId, String toId, EdgeType type, double priorAlpha, double priorBeta) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) return null;
        ReflexGraphEdge existing = findEdge(fromId, toId, type);
        if (existing != null) return existing;
        ReflexGraphEdge e = new ReflexGraphEdge(fromId, toId, type, priorAlpha, priorBeta);
        edges.add(e);
        adjacencyDirty = true;
        return e;
    }

    public List<ReflexGraphEdge> allEdges() {
        return Collections.unmodifiableList(edges);
    }

    public void removeEdge(String fromId, String toId, EdgeType type) {
        edges.removeIf(e -> e.fromId.equals(fromId) && e.toId.equals(toId) && e.type == type);
        adjacencyDirty = true;
    }

    public ReflexGraphEdge findEdge(String fromId, String toId, EdgeType type) {
        for (ReflexGraphEdge e : edges) {
            if (e.fromId.equals(fromId) && e.toId.equals(toId) && e.type == type) return e;
        }
        return null;
    }

    public ReflexGraphEdge findAnyEdge(String fromId, String toId) {
        for (ReflexGraphEdge e : edges) {
            if (e.fromId.equals(fromId) && e.toId.equals(toId)) return e;
        }
        return null;
    }

    // ── Adjacency ──

    private void rebuildAdjacency() {
        outEdges = new HashMap<>();
        inEdges = new HashMap<>();
        for (ReflexGraphEdge e : edges) {
            if (e.pruned) continue;
            outEdges.computeIfAbsent(e.fromId, k -> new ArrayList<>()).add(e);
            inEdges.computeIfAbsent(e.toId, k -> new ArrayList<>()).add(e);
        }
        adjacencyDirty = false;
    }

    private void ensureAdjacency() {
        if (adjacencyDirty) rebuildAdjacency();
    }

    public List<ReflexGraphEdge> getOutEdges(String reflexId) {
        ensureAdjacency();
        return outEdges.getOrDefault(reflexId, List.of());
    }

    public List<ReflexGraphEdge> getInEdges(String reflexId) {
        ensureAdjacency();
        return inEdges.getOrDefault(reflexId, List.of());
    }

    public Set<String> getSuccessors(String reflexId) {
        ensureAdjacency();
        return getOutEdges(reflexId).stream().map(e -> e.toId).collect(Collectors.toSet());
    }

    public Set<String> getPredecessors(String reflexId) {
        ensureAdjacency();
        return getInEdges(reflexId).stream().map(e -> e.fromId).collect(Collectors.toSet());
    }

    public Set<String> getInhibitedBy(String reflexId) {
        ensureAdjacency();
        Set<String> result = new HashSet<>();
        for (var e : getInEdges(reflexId)) {
            if (e.type == EdgeType.INHIBITS) result.add(e.fromId);
        }
        return result;
    }

    // ── Topological sort (Kahn) ──

    public List<String> topologicalSort() {
        ensureAdjacency();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : nodes.keySet()) inDegree.put(id, 0);
        for (ReflexGraphEdge e : edges) {
            if (e.pruned || e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
            inDegree.merge(e.toId, 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            result.add(cur);
            for (ReflexGraphEdge e : getOutEdges(cur)) {
                if (e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
                int deg = inDegree.merge(e.toId, -1, Integer::sum);
                if (deg == 0) queue.add(e.toId);
            }
        }
        return result;
    }

    // ── Cycle detection (DFS three-color) ──

    public boolean hasCycle() {
        ensureAdjacency();
        Set<String> white = new HashSet<>(nodes.keySet());
        Set<String> gray = new HashSet<>();
        Set<String> black = new HashSet<>();

        while (!white.isEmpty()) {
            String start = white.iterator().next();
            if (dfsCycle(start, white, gray, black)) return true;
        }
        return false;
    }

    private boolean dfsCycle(String node, Set<String> white, Set<String> gray, Set<String> black) {
        move(node, white, gray);
        for (ReflexGraphEdge e : getOutEdges(node)) {
            if (e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
            String next = e.toId;
            if (black.contains(next)) continue;
            if (gray.contains(next)) return true;
            if (dfsCycle(next, white, gray, black)) return true;
        }
        move(node, gray, black);
        return false;
    }

    private static void move(String node, Set<String> from, Set<String> to) {
        from.remove(node);
        to.add(node);
    }

    // ── Reachability (BFS) ──

    public boolean reachable(String from, String to) {
        ensureAdjacency();
        if (from.equals(to)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (ReflexGraphEdge e : getOutEdges(cur)) {
                if (e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
                if (e.toId.equals(to)) return true;
                if (visited.add(e.toId)) queue.add(e.toId);
            }
        }
        return false;
    }

    public List<String> bfs(String start) {
        ensureAdjacency();
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (ReflexGraphEdge e : getOutEdges(cur)) {
                if (e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
                if (visited.add(e.toId)) queue.add(e.toId);
            }
        }
        return new ArrayList<>(visited);
    }

    // ── Ready nodes (for scheduler) ──

    public List<ReflexGraphNode> getReadyNodes(Set<String> completed, NeuroState state) {
        ensureAdjacency();
        List<ReflexGraphNode> ready = new ArrayList<>();
        for (ReflexGraphNode n : nodes.values()) {
            if (completed.contains(n.reflexId)) continue;
            List<ReflexGraphEdge> in = getInEdges(n.reflexId);
            boolean allMet = in.stream().allMatch(e ->
                    e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE || completed.contains(e.fromId));
            if (allMet) ready.add(n);
        }
        ready.sort((a, b) -> {
            double sa = a.baseWeight;
            double sb = b.baseWeight;
            return Double.compare(sb, sa);
        });
        return ready;
    }

    // ── Edge outcome recording (Bayesian + compression) ──

    public void recordEdgeOutcome(String fromId, String toId, boolean success, NeuroState state) {
        ReflexGraphEdge edge = findAnyEdge(fromId, toId);
        if (edge == null) return;
        edge.recordActivation(success, state);
        if (success) edge.compress();
        ReflexGraphNode toNode = nodes.get(toId);
        if (toNode != null) {
            toNode.totalAttempts++;
            if (success) {
                toNode.consecutiveFailures = 0;
                toNode.successRate = (toNode.successRate * (toNode.totalAttempts - 1) + 1.0) / toNode.totalAttempts;
            } else {
                toNode.consecutiveFailures++;
                toNode.successRate = (toNode.successRate * (toNode.totalAttempts - 1) + 0.0) / toNode.totalAttempts;
            }
            toNode.lastUsed = System.currentTimeMillis();
            toNode.usageCount++;
        }
    }

    // ── Dynamic weight ──

    public double getDynamicWeight(String fromId, String toId, NeuroState state) {
        ReflexGraphEdge edge = findAnyEdge(fromId, toId);
        return edge != null ? edge.dynamicWeight(state) : 0.0;
    }

    // ── Subgraph by category prefix ──

    public ReflexGraph subgraphByCategory(String prefix) {
        ReflexGraph sub = new ReflexGraph();
        SubgraphBuilder.byCategoryPrefix(this, sub, prefix,
                EdgeType.PRECEDES, EdgeType.DEPENDS_ON, EdgeType.COMPRESSES);
        return sub;
    }

    public ReflexGraph subgraphByNodeIds(Set<String> ids) {
        ReflexGraph sub = new ReflexGraph();
        SubgraphBuilder.byNodeIds(this, sub, ids,
                EdgeType.PRECEDES, EdgeType.DEPENDS_ON, EdgeType.COMPRESSES);
        return sub;
    }

    public static class SubgraphBuilder {
        public static void byCategoryPrefix(ReflexGraph src, ReflexGraph dst,
                                             String prefix, EdgeType... allowedTypes) {
            Set<EdgeType> allowed = allowedTypes.length > 0
                    ? Set.of(allowedTypes) : Set.of(EdgeType.values());
            for (ReflexGraphNode n : src.nodes.values()) {
                String key = n.category != null ? n.category : n.reflexId;
                if (prefix == null || key.startsWith(prefix)) {
                    dst.addNode(n.reflexId, n.baseWeight, n.proficiency, n.atomCount);
                }
            }
            for (ReflexGraphEdge e : src.edges) {
                if (!allowed.contains(e.type) || e.pruned) continue;
                if (dst.nodes.containsKey(e.fromId) && dst.nodes.containsKey(e.toId)) {
                    ReflexGraphEdge ne = dst.addEdge(e.fromId, e.toId, e.type, e.priorAlpha, e.priorBeta);
                    if (ne != null) {
                        ne.alphaSuccess = e.alphaSuccess;
                        ne.betaFailure = e.betaFailure;
                        ne.compression = e.compression;
                        ne.activationCount = e.activationCount;
                        ne.isPassive = e.isPassive;
                    }
                }
            }
            dst.adjacencyDirty = true;
        }

        public static void byNodeIds(ReflexGraph src, ReflexGraph dst,
                                      Set<String> ids, EdgeType... allowedTypes) {
            Set<EdgeType> allowed = allowedTypes.length > 0
                    ? Set.of(allowedTypes) : Set.of(EdgeType.values());
            for (String id : ids) {
                ReflexGraphNode n = src.nodes.get(id);
                if (n != null) {
                    dst.addNode(n.reflexId, n.baseWeight, n.proficiency, n.atomCount);
                }
            }
            for (ReflexGraphEdge e : src.edges) {
                if (!allowed.contains(e.type) || e.pruned) continue;
                if (dst.nodes.containsKey(e.fromId) && dst.nodes.containsKey(e.toId)) {
                    ReflexGraphEdge ne = dst.addEdge(e.fromId, e.toId, e.type, e.priorAlpha, e.priorBeta);
                    if (ne != null) {
                        ne.alphaSuccess = e.alphaSuccess;
                        ne.betaFailure = e.betaFailure;
                        ne.compression = e.compression;
                        ne.activationCount = e.activationCount;
                        ne.isPassive = e.isPassive;
                    }
                }
            }
            dst.adjacencyDirty = true;
        }
    }

    // ── Auto-detect bottlenecks ──

    public void autoDetectBottlenecks() {
        ensureAdjacency();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : nodes.keySet()) inDegree.put(id, 0);
        for (ReflexGraphEdge e : edges) {
            if (e.pruned) continue;
            if (e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
            inDegree.merge(e.toId, 1, Integer::sum);
        }
        for (var entry : inDegree.entrySet()) {
            ReflexGraphNode n = nodes.get(entry.getKey());
            if (n != null && entry.getValue() >= 3) {
                n.isBottleneck = true;
            }
        }
    }

    // ── Lazy pruning ──

    public void lazyPrune(NeuroState state) {
        double threshold = 0.1 + (1.0 - state.serotonin()) * 0.15;
        long now = System.currentTimeMillis();
        for (ReflexGraphEdge e : edges) {
            if (e.pruned) continue;
            if (e.posteriorMean() < threshold
                    && e.activationCount < PRUNE_ACTIVATION_MIN
                    && now - e.lastActivated > PRUNE_IDLE_MS) {
                e.pruned = true;
            }
        }
    }

    public void unprune(String fromId, String toId) {
        ReflexGraphEdge e = findAnyEdge(fromId, toId);
        if (e != null) {
            e.pruned = false;
            e.lastActivated = System.currentTimeMillis();
            adjacencyDirty = true;
        }
    }

    // ── Critical path (DAG longest path) ──

    public List<String> criticalPath() {
        List<String> sorted = topologicalSort();
        if (sorted.isEmpty()) return List.of();

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        String start = null;

        for (String id : sorted) {
            dist.put(id, 0.0);
            if (getInEdges(id).stream().allMatch(e -> e.pruned || e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE)) {
                if (start == null) start = id;
            }
        }

        for (String u : sorted) {
            for (ReflexGraphEdge e : getOutEdges(u)) {
                if (e.pruned || e.type == EdgeType.INHIBITS || e.type == EdgeType.ALTERNATIVE) continue;
                double w = e.avgExecutionTicks > 0 ? e.avgExecutionTicks : CRITICAL_PATH_TICK_PER_ATOM;
                w *= e.compression;
                if (dist.get(e.toId) < dist.get(u) + w) {
                    dist.put(e.toId, dist.get(u) + w);
                    prev.put(e.toId, u);
                }
            }
        }

        String end = sorted.stream().max(Comparator.comparingDouble(dist::get)).orElse(null);
        if (end == null) return List.of();

        List<String> path = new ArrayList<>();
        for (String at = end; at != null; at = prev.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        return path;
    }

    // ── Path satisfaction ──

    public double pathSatisfaction(List<String> path, NeuroState state, Perspective perspective) {
        if (path == null || path.size() < 2) return 0.0;
        double totalScore = 0.0;
        int edgeCount = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            ReflexGraphEdge e = findAnyEdge(path.get(i), path.get(i + 1));
            if (e == null) continue;
            double dw = e.dynamicWeight(state);
            double timeScore = timeScoreForEdge(e, state);
            totalScore += dw * timeScore;
            edgeCount++;
        }
        return edgeCount > 0 ? totalScore / edgeCount : 0.0;
    }

    private double timeScoreForEdge(ReflexGraphEdge e, NeuroState state) {
        double ts = 0.5 + state.ne() * 0.8 + (1.0 - state.da()) * 0.3 + state.serotonin() * 0.15;
        double perceivedTicks = e.avgExecutionTicks * e.compression * ts;
        double maxTicks = 600.0;
        return Math.max(0, 1.0 - perceivedTicks / maxTicks);
    }

    // ── Consolidation from MemoryGraph ──

    public void consolidateFromMemoryGraph(
            com.izimi.eagent.hippocampus.MemoryGraph mg,
            com.izimi.eagent.hippocampus.MemoryManager memMgr) {
        if (mg == null) return;
        for (MemoryEdge me : mg.getEdges()) {
            if (me.weight() < CONSOLIDATION_THRESHOLD) continue;
            MemoryEdge.RelationType memType = me.type();
            String skillA = inferReflexFromMemoryEdge(me.fromId(), mg, memMgr);
            String skillB = inferReflexFromMemoryEdge(me.toId(), mg, memMgr);
            if (skillA != null && skillB != null && !skillA.equals(skillB)) {
                String reflexA = "reflex_" + skillA;
                String reflexB = "reflex_" + skillB;
                if (nodes.containsKey(reflexA) && nodes.containsKey(reflexB)) {
                    EdgeType reflexType = mapMemoryTypeToReflexType(memType);
                    double priorAlpha = 3.0 / (1.0 + me.weight());
                    if (findEdge(reflexA, reflexB, reflexType) == null) {
                        addEdge(reflexA, reflexB, reflexType, priorAlpha, 1.0);
                    }
                }
            }
        }
    }

    private String inferReflexFromMemoryEdge(String memoryId,
                                              com.izimi.eagent.hippocampus.MemoryGraph mg,
                                              com.izimi.eagent.hippocampus.MemoryManager memMgr) {
        if (memMgr == null) return null;
        com.izimi.eagent.hippocampus.MemoryEntry entry = memMgr.getEntry(memoryId);
        if (entry == null || entry.relatedSkills == null) return null;
        for (String skill : entry.relatedSkills) {
            if (skill != null && !skill.isEmpty()) return skill;
        }
        return null;
    }

    public static EdgeType mapMemoryTypeToReflexType(MemoryEdge.RelationType memType) {
        return switch (memType) {
            case CAUSAL -> EdgeType.PRECEDES;
            case TEMPORAL -> EdgeType.PRECEDES;
            case SIMILARITY -> EdgeType.ALTERNATIVE;
            case CONTRAST -> EdgeType.INHIBITS;
        };
    }

    // ── Build from TaskDAG ──

    public static ReflexGraph fromTaskDAG(TaskDAG dag) {
        ReflexGraph graph = new ReflexGraph();
        for (TaskDAG.SubtaskNode sn : dag.nodes()) {
            graph.addNode(sn.id(), 0.5, 0.3, 1);
        }
        for (TaskDAG.SubtaskNode sn : dag.nodes()) {
            for (TaskDAG.Dependency dep : sn.dependsOn()) {
                graph.addEdge(dep.id(), sn.id(), dep.type().equals("hard") ? EdgeType.DEPENDS_ON : EdgeType.ALTERNATIVE);
            }
        }
        graph.autoDetectBottlenecks();
        return graph;
    }

    // ── Persistence ──

    public void save(Path path) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", 1);

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (ReflexGraphNode n : nodes.values()) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("reflexId", n.reflexId);
            nm.put("displayName", n.displayName);
            nm.put("category", n.category);
            nm.put("baseWeight", n.baseWeight);
            nm.put("proficiency", n.proficiency);
            nm.put("atomCount", n.atomCount);
            nm.put("isBottleneck", n.isBottleneck);
            nm.put("successRate", n.successRate);
            nm.put("totalAttempts", n.totalAttempts);
            nm.put("consecutiveFailures", n.consecutiveFailures);
            nm.put("lastUsed", n.lastUsed);
            nm.put("usageCount", n.usageCount);
            nodeList.add(nm);
        }
        data.put("nodes", nodeList);

        List<Map<String, Object>> edgeList = new ArrayList<>();
        for (ReflexGraphEdge e : edges) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("from", e.fromId);
            em.put("to", e.toId);
            em.put("type", e.type.name());
            em.put("priorAlpha", e.priorAlpha);
            em.put("priorBeta", e.priorBeta);
            em.put("alphaSuccess", e.alphaSuccess);
            em.put("betaFailure", e.betaFailure);
            em.put("avgExecutionTicks", e.avgExecutionTicks);
            em.put("compression", e.compression);
            em.put("activationCount", e.activationCount);
            em.put("lastActivated", e.lastActivated);
            em.put("neSensitivity", e.neSensitivity);
            em.put("daSensitivity", e.daSensitivity);
            em.put("isPassive", e.isPassive);
            em.put("pruned", e.pruned);
            edgeList.add(em);
        }
        data.put("edges", edgeList);

        JsonUtil.writeToFileSafe(path, data);
    }

    @SuppressWarnings("unchecked")
    public static ReflexGraph load(Path path) {
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return new ReflexGraph();
        ReflexGraph graph = new ReflexGraph();

        Object rawNodes = data.get("nodes");
        if (rawNodes instanceof List) {
            for (Object raw : (List<?>) rawNodes) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> nm = (Map<String, Object>) raw;
                String reflexId = (String) nm.get("reflexId");
                if (reflexId == null) continue;
                ReflexGraphNode n = graph.addNode(reflexId,
                        ((Number) nm.getOrDefault("baseWeight", 0.5)).doubleValue(),
                        ((Number) nm.getOrDefault("proficiency", 0.3)).doubleValue(),
                        ((Number) nm.getOrDefault("atomCount", 1)).intValue());
                n.displayName = (String) nm.getOrDefault("displayName", reflexId);
                n.category = (String) nm.get("category");
                n.isBottleneck = Boolean.TRUE.equals(nm.get("isBottleneck"));
                n.successRate = ((Number) nm.getOrDefault("successRate", 0.5)).doubleValue();
                n.totalAttempts = ((Number) nm.getOrDefault("totalAttempts", 0)).intValue();
                n.consecutiveFailures = ((Number) nm.getOrDefault("consecutiveFailures", 0)).intValue();
                n.lastUsed = ((Number) nm.getOrDefault("lastUsed", System.currentTimeMillis())).longValue();
                n.usageCount = ((Number) nm.getOrDefault("usageCount", 0)).intValue();
            }
        }

        Object rawEdges = data.get("edges");
        if (rawEdges instanceof List) {
            for (Object raw : (List<?>) rawEdges) {
                if (!(raw instanceof Map)) continue;
                Map<String, Object> em = (Map<String, Object>) raw;
                String from = (String) em.get("from");
                String to = (String) em.get("to");
                String typeStr = (String) em.get("type");
                if (from == null || to == null || typeStr == null) continue;
                try {
                    EdgeType type = EdgeType.valueOf(typeStr);
                    ReflexGraphEdge e = graph.addEdge(from, to, type,
                            ((Number) em.getOrDefault("priorAlpha", DEFAULT_PRIOR_ALPHA)).doubleValue(),
                            ((Number) em.getOrDefault("priorBeta", DEFAULT_PRIOR_BETA)).doubleValue());
                    if (e != null) {
                        e.alphaSuccess = ((Number) em.getOrDefault("alphaSuccess", 0)).intValue();
                        e.betaFailure = ((Number) em.getOrDefault("betaFailure", 0)).intValue();
                        e.avgExecutionTicks = ((Number) em.getOrDefault("avgExecutionTicks", 0)).intValue();
                        e.compression = ((Number) em.getOrDefault("compression", 1.0)).doubleValue();
                        e.activationCount = ((Number) em.getOrDefault("activationCount", 0)).intValue();
                        e.lastActivated = ((Number) em.getOrDefault("lastActivated", System.currentTimeMillis())).longValue();
                        e.neSensitivity = ((Number) em.getOrDefault("neSensitivity", 1.0)).doubleValue();
                        e.daSensitivity = ((Number) em.getOrDefault("daSensitivity", 1.0)).doubleValue();
                        e.isPassive = Boolean.TRUE.equals(em.get("isPassive"));
                        e.pruned = Boolean.TRUE.equals(em.get("pruned"));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        graph.adjacencyDirty = true;
        return graph;
    }

    // ── Debug ──

    @Override
    public String toString() {
        return "ReflexGraph{" + nodeCount() + " nodes, " + edges.size() + " edges}";
    }
}
