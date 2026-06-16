package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReflexGraphTest {

    private ReflexGraph graph;

    @BeforeEach
    void setUp() {
        graph = new ReflexGraph();
    }

    @Test
    @DisplayName("addNode creates node and stores it")
    void addNodeCreatesNode() {
        ReflexGraph.ReflexGraphNode n = graph.addNode("dig_stone", 0.5, 0.3, 3);
        assertNotNull(n);
        assertEquals("dig_stone", n.reflexId());
        assertEquals(0.5, n.baseWeight());
        assertEquals(1, graph.nodeCount());
    }

    @Test
    @DisplayName("getNode returns null for non-existent node")
    void getNodeMissing() {
        assertNull(graph.getNode("nonexistent"));
    }

    @Test
    @DisplayName("addEdge creates edge with correct type")
    void addEdgeCreatesEdge() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        assertNotNull(e);
        assertEquals("a", e.fromId());
        assertEquals("b", e.toId());
        assertEquals(ReflexGraph.EdgeType.PRECEDES, e.type());
    }

    @Test
    @DisplayName("addEdge returns null when nodes don't exist")
    void addEdgeMissingNodes() {
        assertNull(graph.addEdge("nonexistent", "b", ReflexGraph.EdgeType.PRECEDES));
    }

    @Test
    @DisplayName("addEdge de-duplicates: same from/to/type returns existing")
    void addEdgeDeDuplicates() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e1 = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        ReflexGraph.ReflexGraphEdge e2 = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        assertSame(e1, e2);
    }

    @Test
    @DisplayName("findEdge finds by from/to/type")
    void findEdgeByType() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        assertNotNull(graph.findEdge("a", "b", ReflexGraph.EdgeType.PRECEDES));
        assertNull(graph.findEdge("a", "b", ReflexGraph.EdgeType.INHIBITS));
    }

    @Test
    @DisplayName("getSuccessors and getPredecessors return correct sets")
    void successorsAndPredecessors() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("a", "c", ReflexGraph.EdgeType.DEPENDS_ON);

        assertEquals(Set.of("b", "c"), graph.getSuccessors("a"));
        assertEquals(Set.of(), graph.getPredecessors("a"));
        assertEquals(Set.of("a"), graph.getPredecessors("b"));
    }

    @Test
    @DisplayName("topologicalSort returns linear order respecting edges")
    void topologicalSortLinear() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.DEPENDS_ON);

        List<String> sorted = graph.topologicalSort();
        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0));
        assertEquals("b", sorted.get(1));
        assertEquals("c", sorted.get(2));
    }

    @Test
    @DisplayName("topologicalSort excludes INHIBITS and ALTERNATIVE edges")
    void topologicalSortExcludesNonDagEdges() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.INHIBITS);
        List<String> sorted = graph.topologicalSort();
        assertTrue(sorted.contains("a"));
        assertTrue(sorted.contains("b"));
    }

    @Test
    @DisplayName("hasCycle returns false for DAG")
    void hasCycleFalse() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.PRECEDES);
        assertFalse(graph.hasCycle());
    }

    @Test
    @DisplayName("hasCycle returns true for cycle")
    void hasCycleTrue() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("c", "a", ReflexGraph.EdgeType.PRECEDES);
        assertTrue(graph.hasCycle());
    }

    @Test
    @DisplayName("reachable returns correct connectivity")
    void reachable() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.PRECEDES);

        assertTrue(graph.reachable("a", "c"));
        assertFalse(graph.reachable("c", "a"));
    }

    @Test
    @DisplayName("bfs returns all reachable nodes from start")
    void bfsReturnsReachable() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.PRECEDES);

        List<String> visited = graph.bfs("a");
        assertTrue(visited.contains("b"));
        assertTrue(visited.contains("c"));
    }

    @Test
    @DisplayName("edge posteriorMean uses prior + counts")
    void edgePosteriorMean() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES, 2.0, 2.0);
        assertEquals(0.5, e.posteriorMean(), 0.001);
        graph.recordEdgeOutcome("a", "b", true, new NeuroState(0.5, 0.5, 0.5, 0.5));
        assertEquals(3.0 / 5.0, e.posteriorMean(), 0.001);
    }

    @Test
    @DisplayName("hormoneModulation differs with NE and DA")
    void hormoneModulation() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.DEPENDS_ON);
        double base = e.dynamicWeight(new NeuroState(0.5, 0.5, 0.5, 0.5));
        double highDA = e.dynamicWeight(new NeuroState(0.5, 0.9, 0.5, 0.5));
        assertNotEquals(base, highDA, 0.001, "DA should modulate weight");
    }

    @Test
    @DisplayName("pruned edge returns 0 dynamicWeight")
    void prunedEdgeReturnsZero() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        e.pruned = true;
        assertEquals(0.0, graph.getDynamicWeight("a", "b", new NeuroState(0.5, 0.5, 0.5, 0.5)));
    }

    @Test
    @DisplayName("lazyPrune marks low-weight, low-activation, idle edges as pruned")
    void lazyPrune() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES, 0.1, 2.0);
        e.lastActivated = System.currentTimeMillis() - ReflexGraph.PRUNE_IDLE_MS - 1;
        e.activationCount = 0;
        graph.lazyPrune(new NeuroState(0.5, 0.5, 0.5, 0.5));
        assertTrue(e.pruned);
    }

    @Test
    @DisplayName("unprune restores edge")
    void unprune() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        ReflexGraph.ReflexGraphEdge e = graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        e.pruned = true;
        graph.unprune("a", "b");
        assertFalse(e.pruned);
    }

    @Test
    @DisplayName("criticalPath returns longest path in DAG")
    void criticalPath() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "c", ReflexGraph.EdgeType.PRECEDES);
        List<String> cp = graph.criticalPath();
        assertEquals(List.of("a", "b", "c"), cp);
    }

    @Test
    @DisplayName("getReadyNodes returns nodes with all non-ALTERNATIVE/INHIBIT predecessors completed")
    void getReadyNodes() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        List<ReflexGraph.ReflexGraphNode> ready = graph.getReadyNodes(Set.of(), new NeuroState(0.5, 0.5, 0.5, 0.5));
        assertEquals(1, ready.size());
        assertEquals("a", ready.get(0).reflexId());
    }

    @Test
    @DisplayName("autoDetectBottlenecks marks nodes with in-degree >= 3")
    void autoDetectBottlenecks() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addNode("hub", 0.5, 0.3, 1);
        graph.addEdge("a", "hub", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("b", "hub", ReflexGraph.EdgeType.PRECEDES);
        graph.addEdge("c", "hub", ReflexGraph.EdgeType.PRECEDES);
        graph.autoDetectBottlenecks();
        assertTrue(graph.getNode("hub").isBottleneck());
        assertFalse(graph.getNode("a").isBottleneck());
    }

    @Test
    @DisplayName("save and load round-trips data correctly")
    void saveAndLoad() throws Exception {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.7, 0.5, 2);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);
        Path tmp = java.nio.file.Files.createTempFile("reflexgraph", ".json");
        graph.save(tmp);
        ReflexGraph loaded = ReflexGraph.load(tmp);
        assertEquals(2, loaded.nodeCount());
        assertNotNull(loaded.getNode("a"));
        assertNotNull(loaded.getNode("b"));
        assertNotNull(loaded.findEdge("a", "b", ReflexGraph.EdgeType.PRECEDES));
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    @DisplayName("fromTaskDAG builds graph from TaskDAG")
    void fromTaskDAG() {
        var dep = new com.izimi.eagent.cortex.planner.TaskDAG.Dependency("dig", "hard", 1.0, List.of());
        var nodes = List.of(
                new com.izimi.eagent.cortex.planner.TaskDAG.SubtaskNode("dig", "dig_stone", "dig", "stone", 1, List.of(), false),
                new com.izimi.eagent.cortex.planner.TaskDAG.SubtaskNode("craft", "craft_table", "craft", "table", 1, List.of(dep), false)
        );
        var dag = new com.izimi.eagent.cortex.planner.TaskDAG("task_1", nodes);
        ReflexGraph g = ReflexGraph.fromTaskDAG(dag);
        assertEquals(2, g.nodeCount());
        assertNotNull(g.findEdge("dig", "craft", ReflexGraph.EdgeType.DEPENDS_ON));
    }
}
