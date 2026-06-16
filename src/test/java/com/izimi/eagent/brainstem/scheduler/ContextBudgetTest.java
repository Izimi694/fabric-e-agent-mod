package com.izimi.eagent.brainstem.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContextBudgetTest {

    private ReflexGraph graph;

    @BeforeEach
    void setUp() {
        graph = new ReflexGraph();
    }

    @Test
    @DisplayName("getBudget returns different budgets per perspective")
    void getBudget() {
        assertTrue(ContextBudget.getBudget(Perspective.SURVIVAL) < ContextBudget.getBudget(Perspective.CURIOUS));
        assertEquals(1200, ContextBudget.getBudget(Perspective.TASK));
    }

    @Test
    @DisplayName("matchesCategory filters by perspective prefix")
    void matchesCategory() {
        assertTrue(ContextBudget.matchesCategory(Perspective.SURVIVAL, "attack_zombie", "attack_hostile"));
        assertFalse(ContextBudget.matchesCategory(Perspective.SOCIAL, "dig_stone", "dig_common_block"));
        assertTrue(ContextBudget.matchesCategory(Perspective.CURIOUS, "dig_stone", "dig_common_block"));
    }

    @Test
    @DisplayName("filterByPerspective returns empty for unmatched SURVIVAL")
    void filterByPerspectiveEmpty() {
        graph.addNode("dig_stone", 0.5, 0.3, 2).category = "dig_common_block";
        var filtered = ContextBudget.filterByPerspective(
                Perspective.SURVIVAL, graph.allNodes(), null);
        assertTrue(filtered.isEmpty());
    }

    @Test
    @DisplayName("filterByPerspective returns matching nodes by category prefix")
    void filterByPerspectiveMatches() {
        graph.addNode("attack_zombie", 0.7, 0.5, 2).category = "attack_hostile";
        graph.addNode("dig_stone", 0.5, 0.3, 2).category = "dig_common_block";
        var filtered = ContextBudget.filterByPerspective(
                Perspective.SURVIVAL, graph.allNodes(), null);
        assertEquals(1, filtered.size());
        assertEquals("attack_zombie", filtered.get(0).reflexId());
    }

    @Test
    @DisplayName("filterByPerspective with TASK matches goal keyword")
    void filterByPerspectiveTask() {
        graph.addNode("dig_stone", 0.5, 0.3, 2).category = "dig_common_block";
        graph.addNode("craft_table", 0.5, 0.3, 2).category = "craft_workbench";
        var filtered = ContextBudget.filterByPerspective(
                Perspective.TASK, graph.allNodes(), "craft");
        assertEquals(1, filtered.size());
        assertEquals("craft_table", filtered.get(0).reflexId());
    }

    @Test
    @DisplayName("compileReflexSummary respects budget limits")
    void compileReflexSummaryRespectsBudget() {
        for (int i = 0; i < 20; i++) {
            var n = graph.addNode("reflex_" + i, 0.5, 0.3, 1);
            n.category = "dig_common_block";
        }
        String summary = ContextBudget.compileReflexSummary(
                Perspective.SOCIAL, graph.allNodes(), graph.allEdges(), null);
        assertTrue(summary.contains("预算"), "Summary should contain budget line");
        assertTrue(summary.length() < 5000, "Summary should not exceed reasonable length");
    }

    @Test
    @DisplayName("toCompactSummary includes key fields")
    void toCompactSummary() {
        var n = graph.addNode("dig_stone", 0.5, 0.3, 2);
        n.category = "dig_common_block";
        n.successRate = 0.75;
        String s = n.toCompactSummary();
        assertTrue(s.contains("dig_stone"));
        assertTrue(s.contains("dig_common_block"));
        assertTrue(s.contains("0.75"));
    }

    @Test
    @DisplayName("SubgraphBuilder.byCategoryPrefix creates valid subgraph")
    void subgraphByCategoryPrefix() {
        graph.addNode("attack_zombie", 0.5, 0.3, 1).category = "attack_hostile";
        graph.addNode("attack_cow", 0.5, 0.3, 1).category = "attack_passive";
        graph.addNode("dig_stone", 0.5, 0.3, 1).category = "dig_common_block";
        graph.addEdge("attack_zombie", "attack_cow", ReflexGraph.EdgeType.ALTERNATIVE);

        ReflexGraph sub = graph.subgraphByCategory("attack_");
        assertEquals(2, sub.nodeCount());
        assertNotNull(sub.getNode("attack_zombie"));
        assertNotNull(sub.getNode("attack_cow"));
    }

    @Test
    @DisplayName("SubgraphBuilder.byNodeIds filters by node set")
    void subgraphByNodeIds() {
        graph.addNode("a", 0.5, 0.3, 1);
        graph.addNode("b", 0.5, 0.3, 1);
        graph.addNode("c", 0.5, 0.3, 1);
        graph.addEdge("a", "b", ReflexGraph.EdgeType.PRECEDES);

        ReflexGraph sub = graph.subgraphByNodeIds(Set.of("a", "c"));
        assertEquals(2, sub.nodeCount());
        assertNotNull(sub.getNode("a"));
        assertNotNull(sub.getNode("c"));
    }
}
