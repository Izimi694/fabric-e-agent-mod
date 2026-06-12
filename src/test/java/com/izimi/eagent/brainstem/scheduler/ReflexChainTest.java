package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.planner.TaskDAG;
import com.izimi.eagent.cortex.planner.TaskDAG.Dependency;
import com.izimi.eagent.cortex.planner.TaskDAG.SubtaskNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReflexChainTest {

    private ReflexChain chain;

    @BeforeEach
    void setUp() {
        chain = new ReflexChain("test_task");
    }

    @Test
    @DisplayName("addNode creates node and stores it")
    void addNodeCreatesNode() {
        ReflexChain.ReflexNode node = chain.addNode("n1", "dig_stone", 0.5, false);
        assertNotNull(node);
        assertEquals("n1", node.id());
        assertEquals("dig_stone", node.reflexId());
        assertFalse(node.isBottleneck());
    }

    @Test
    @DisplayName("link creates directional edge between nodes")
    void linkCreatesEdge() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.addNode("n2", "craft_table", 0.5, false);
        chain.link("n1", "n2");

        ReflexChain.ReflexNode n1 = chain.getNode("n1");
        ReflexChain.ReflexNode n2 = chain.getNode("n2");
        assertTrue(n1.next().contains("n2"));
        assertTrue(n2.prev().contains("n1"));
    }

    @Test
    @DisplayName("link does nothing for non-existent nodes")
    void linkMissingNodes() {
        chain.link("nonexistent1", "nonexistent2");
        assertTrue(chain.allNodes().isEmpty());
    }

    @Test
    @DisplayName("getReadyNodes returns nodes with all dependencies met")
    void getReadyNodesRespectsDependencies() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.addNode("n2", "craft_table", 0.5, false);
        chain.link("n1", "n2");

        Set<String> completed = Set.of("n1");
        List<ReflexChain.ReflexNode> ready = chain.getReadyNodes(completed);
        assertEquals(1, ready.size());
        assertEquals("n2", ready.get(0).id());
    }

    @Test
    @DisplayName("getReadyNodes returns roots when nothing completed")
    void getReadyNodesReturnsRoots() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.addNode("n2", "craft_table", 0.5, false);
        chain.link("n1", "n2");

        List<ReflexChain.ReflexNode> ready = chain.getReadyNodes(Set.of());
        assertEquals(1, ready.size());
        assertEquals("n1", ready.get(0).id());
    }

    @Test
    @DisplayName("getNextCandidate skips IDs in afterSkip set")
    void getNextCandidateSkips() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.addNode("n2", "craft_table", 0.5, false);
        chain.addNode("n3", "mine_iron", 0.5, false);

        List<ReflexChain.ReflexNode> candidates = List.of(
                chain.getNode("n1"), chain.getNode("n2"), chain.getNode("n3"));
        ReflexChain.ReflexNode result = chain.getNextCandidate(Set.of("n1", "n3"), candidates);
        assertNotNull(result);
        assertEquals("n2", result.id());
    }

    @Test
    @DisplayName("getNextCandidate returns null when all skipped")
    void getNextCandidateAllSkipped() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.addNode("n2", "craft_table", 0.5, false);

        ReflexChain.ReflexNode result = chain.getNextCandidate(
                Set.of("n1", "n2"), List.of(chain.getNode("n1"), chain.getNode("n2")));
        assertNull(result);
    }

    @Test
    @DisplayName("traceUpstream returns transitive predecessors")
    void traceUpstreamTransitive() {
        chain.addNode("n1", "get_wood", 0.5, false);
        chain.addNode("n2", "craft_planks", 0.5, false);
        chain.addNode("n3", "craft_table", 0.5, false);
        chain.link("n1", "n2");
        chain.link("n2", "n3");

        List<String> upstream = chain.traceUpstream("n3");
        assertTrue(upstream.contains("n2"));
        assertTrue(upstream.contains("n1"));
    }

    @Test
    @DisplayName("buildFromDAG creates chain mirroring DAG structure")
    void buildFromDAGCreatesChain() {
        List<SubtaskNode> dagNodes = List.of(
                new SubtaskNode("n1", "Get Wood", "dig", "wood", 1, List.of(), false),
                new SubtaskNode("n2", "Craft Planks", "craft", "planks", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false),
                new SubtaskNode("n3", "Craft Table", "craft", "crafting_table", 1,
                        List.of(new Dependency("n2", "hard", 1.0, List.of())), false)
        );
        TaskDAG dag = new TaskDAG("build_table", dagNodes);

        ReflexChain c = ReflexChain.buildFromDAG(dag);
        assertEquals(3, c.allNodes().size());
        assertTrue(c.getNode("n1").next().contains("n2"));
        assertTrue(c.getNode("n2").prev().contains("n1"));
    }

    @Test
    @DisplayName("autoDetectBottlenecks marks nodes with in-degree >= 3")
    void autoDetectBottlenecks() {
        chain.addNode("hub", "central", 0.5, false);
        for (int i = 0; i < 4; i++) {
            chain.addNode("child" + i, "task" + i, 0.5, false);
            chain.link("hub", "child" + i);
        }
        chain.autoDetectBottlenecks();
        assertTrue(chain.getNode("hub").isBottleneck());
    }

    @Test
    @DisplayName("ReflexNode recordOutcome updates success/failure stats")
    void recordOutcomeUpdatesStats() {
        ReflexChain.ReflexNode node = chain.addNode("n1", "dig_stone", 0.5, false);
        node.recordOutcome(true, "task1");
        assertEquals(0, node.getConsecutiveFailures());
        assertEquals(1, node.getSuccessRate());
        node.recordOutcome(false, "task1");
        assertEquals(1, node.getConsecutiveFailures());
        assertEquals(0.5, node.getSuccessRate(), 0.001);
    }

    @Test
    @DisplayName("ReflexNode isStable after 10+ attempts with 0 failures")
    void isStableAfterManyAttempts() {
        ReflexChain.ReflexNode node = chain.addNode("n1", "dig_stone", 0.5, false);
        for (int i = 0; i < 12; i++) {
            node.recordOutcome(true, "task1");
        }
        assertTrue(node.isStable());
    }

    @Test
    @DisplayName("ReflexNode isStable false with failures")
    void isStableFalseWithFailures() {
        ReflexChain.ReflexNode node = chain.addNode("n1", "dig_stone", 0.5, false);
        for (int i = 0; i < 5; i++) {
            node.recordOutcome(true, "task1");
        }
        node.recordOutcome(false, "task1");
        assertFalse(node.isStable());
    }

    @Test
    @DisplayName("ReflexNode getSharedWeight combines baseWeight and taskConfidence")
    void getSharedWeightCombinesFactors() {
        ReflexChain.ReflexNode node = chain.addNode("n1", "dig_stone", 0.8, false);
        node.recordOutcome(true, "task1");
        double sw = node.getSharedWeight("task1");
        assertTrue(sw > 0);
        assertTrue(sw <= 1.0);
    }

    @Test
    @DisplayName("buildFromDAG preserves bottleneck flag")
    void buildFromDAGPreservesBottleneck() {
        List<SubtaskNode> dagNodes = List.of(
                new SubtaskNode("n1", "Hub", "central", "", 1, List.of(), true)
        );
        TaskDAG dag = new TaskDAG("test", dagNodes);
        ReflexChain c = ReflexChain.buildFromDAG(dag);
        assertTrue(c.getNode("n1").isBottleneck());
    }

    @Test
    @DisplayName("onNodeResult returns true on success")
    void onNodeResultSuccess() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        assertTrue(chain.onNodeResult("n1", true, "task1"));
    }

    @Test
    @DisplayName("onNodeResult returns false on consecutive failure")
    void onNodeResultFailure() {
        chain.addNode("n1", "dig_stone", 0.5, false);
        chain.onNodeResult("n1", false, "task1");
        assertFalse(chain.onNodeResult("n1", false, "task1"));
    }
}
