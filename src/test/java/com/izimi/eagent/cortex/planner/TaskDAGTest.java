package com.izimi.eagent.cortex.planner;

import com.izimi.eagent.cortex.planner.TaskDAG.Binding;
import com.izimi.eagent.cortex.planner.TaskDAG.BottleneckDetector;
import com.izimi.eagent.cortex.planner.TaskDAG.Dependency;
import com.izimi.eagent.cortex.planner.TaskDAG.SubtaskNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskDAGTest {

    @Test
    @DisplayName("fromLLMJson parses basic DAG structure")
    void fromLLMJsonParsesBasic() {
        var maps = List.of(
                Map.<String, Object>of("id", "n1", "action", "dig", "target", "wood"),
                Map.<String, Object>of("id", "n2", "action", "craft", "target", "planks",
                        "depends_on", List.of(Map.of("id", "n1")))
        );
        TaskDAG dag = TaskDAG.fromLLMJson("test", maps);
        assertEquals(2, dag.nodes().size());
        assertNotNull(dag.getNode("n1"));
        assertNotNull(dag.getNode("n2"));
    }

    @Test
    @DisplayName("fromLLMJson parses bindings in depends_on")
    void fromLLMJsonParsesBindings() {
        var maps = List.of(
                Map.<String, Object>of("id", "n1", "action", "dig", "target", "iron"),
                Map.<String, Object>of("id", "n2", "action", "smelt", "target", "iron_ingot",
                        "depends_on", List.of(Map.of("id", "n1", "type", "hard", "weight", 1.0,
                                "bindings", List.of(Map.of("from", "output.position", "to", "input.position", "transform", "offset:1")))))
        );
        TaskDAG dag = TaskDAG.fromLLMJson("test", maps);
        SubtaskNode n2 = dag.getNode("n2");
        assertEquals(1, n2.dependsOn().size());
        Dependency dep = n2.dependsOn().get(0);
        assertEquals("hard", dep.type());
        assertEquals(1.0, dep.weight(), 0.001);
        assertEquals(1, dep.bindings().size());
        assertEquals("offset:1", dep.bindings().get(0).transform());
    }

    @Test
    @DisplayName("fromLLMJson marks bottleneck nodes")
    void fromLLMJsonBottleneck() {
        var maps = List.of(
                Map.<String, Object>of("id", "hub", "action", "process", "is_bottleneck", true)
        );
        TaskDAG dag = TaskDAG.fromLLMJson("test", maps);
        assertTrue(dag.getNode("hub").isBottleneck());
    }

    @Test
    @DisplayName("fromLLMJson defaults count to 1")
    void fromLLMJsonDefaultCount() {
        var maps = List.of(Map.<String, Object>of("id", "n1", "action", "dig"));
        TaskDAG dag = TaskDAG.fromLLMJson("test", maps);
        assertEquals(1, dag.getNode("n1").count());
    }

    @Test
    @DisplayName("getReadyNodes returns roots when none completed")
    void getReadyNodesRoots() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "Get Wood", "dig", "wood", 1, List.of(), false),
                new SubtaskNode("n2", "Craft", "craft", "planks", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false)
        );
        TaskDAG dag = new TaskDAG("test", nodes);
        var ready = dag.getReadyNodes(Map.of());
        assertEquals(1, ready.size());
        assertEquals("n1", ready.get(0).id());
    }

    @Test
    @DisplayName("getReadyNodes returns downstream when upstream completed")
    void getReadyNodesDownstream() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "Get Wood", "dig", "wood", 1, List.of(), false),
                new SubtaskNode("n2", "Craft", "craft", "planks", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false)
        );
        TaskDAG dag = new TaskDAG("test", nodes);
        var ready = dag.getReadyNodes(Map.of("n1", true));
        assertEquals(1, ready.size());
        assertEquals("n2", ready.get(0).id());
    }

    @Test
    @DisplayName("getReadyNodes excludes already completed")
    void getReadyNodesExcludesCompleted() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "Get Wood", "dig", "wood", 1, List.of(), false),
                new SubtaskNode("n2", "Craft", "craft", "planks", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false)
        );
        TaskDAG dag = new TaskDAG("test", nodes);
        var ready = dag.getReadyNodes(Map.of("n1", true, "n2", true));
        assertTrue(ready.isEmpty());
    }

    @Test
    @DisplayName("traceUpstream returns all transitive dependencies")
    void traceUpstreamTransitive() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "A", "dig", "", 1, List.of(), false),
                new SubtaskNode("n2", "B", "dig", "", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false),
                new SubtaskNode("n3", "C", "dig", "", 1,
                        List.of(new Dependency("n2", "hard", 1.0, List.of())), false)
        );
        TaskDAG dag = new TaskDAG("test", nodes);
        var upstream = dag.traceUpstream("n3");
        assertTrue(upstream.contains("n2"));
        assertTrue(upstream.contains("n1"));
    }

    @Test
    @DisplayName("traceUpstream returns empty for unknown node")
    void traceUpstreamUnknown() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "A", "dig", "", 1, List.of(), false)
        );
        TaskDAG dag = new TaskDAG("test", nodes);
        assertTrue(dag.traceUpstream("nonexistent").isEmpty());
    }

    // ── BottleneckDetector ──

    @Test
    @DisplayName("BottleneckDetector marks nodes with in-degree >= 3")
    void bottleneckDetectorStructural() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("hub", "Hub", "process", "", 1, List.of(), false),
                new SubtaskNode("c1", "C1", "dig", "", 1,
                        List.of(new Dependency("hub", "hard", 1.0, List.of())), false),
                new SubtaskNode("c2", "C2", "dig", "", 1,
                        List.of(new Dependency("hub", "hard", 1.0, List.of())), false),
                new SubtaskNode("c3", "C3", "dig", "", 1,
                        List.of(new Dependency("hub", "hard", 1.0, List.of())), false)
        );
        var bottlenecks = BottleneckDetector.detectStructural(nodes);
        assertTrue(bottlenecks.contains("hub"));
    }

    @Test
    @DisplayName("BottleneckDetector returns empty for low in-degree")
    void bottleneckDetectorNone() {
        List<SubtaskNode> nodes = List.of(
                new SubtaskNode("n1", "A", "dig", "", 1, List.of(), false),
                new SubtaskNode("n2", "B", "dig", "", 1,
                        List.of(new Dependency("n1", "hard", 1.0, List.of())), false)
        );
        var bottlenecks = BottleneckDetector.detectStructural(nodes);
        assertTrue(bottlenecks.isEmpty());
    }

    @Test
    @DisplayName("Dependency record stores all fields")
    void dependencyRecord() {
        var binding = new Binding("output.pos", "input.pos", "offset:1");
        var dep = new Dependency("n1", "hard", 0.8, List.of(binding));
        assertEquals("n1", dep.id());
        assertEquals("hard", dep.type());
        assertEquals(0.8, dep.weight(), 0.001);
        assertEquals(1, dep.bindings().size());
        assertEquals("output.pos", dep.bindings().get(0).from());
    }

    @Test
    @DisplayName("Binding record stores fields")
    void bindingRecord() {
        var b = new Binding("from_x", "to_y", "nearest(diamond)");
        assertEquals("from_x", b.from());
        assertEquals("to_y", b.to());
        assertEquals("nearest(diamond)", b.transform());
    }
}
