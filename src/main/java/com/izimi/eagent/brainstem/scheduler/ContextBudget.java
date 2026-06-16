package com.izimi.eagent.brainstem.scheduler;

import java.util.*;
import java.util.stream.Collectors;

public class ContextBudget {

    // ── Purpose → token budget ──
    public static final int BUDGET_SURVIVAL = 800;
    public static final int BUDGET_TASK = 1200;
    public static final int BUDGET_SOCIAL = 600;
    public static final int BUDGET_CURIOUS = 1000;
    public static final int BUDGET_CAUTIOUS = 700;
    public static final int BUDGET_DEFAULT = 800;

    // ── Perspective → category prefix filter set ──
    private static final Map<Perspective, Set<String>> CATEGORY_PREFIXES = Map.of(
        Perspective.SURVIVAL, Set.of("attack_", "eat_", "dig_food", "flee_", "equip_armor"),
        Perspective.TASK,     Set.of(),       // unfiltered, matched by goal keywords
        Perspective.SOCIAL,   Set.of("chat_", "interact_", "trade_"),
        Perspective.CURIOUS,  Set.of(),       // unfiltered (full graph)
        Perspective.CAUTIOUS, Set.of("attack_", "flee_", "dig_ore", "place_block", "craft_")
    );

    // ── Token cost estimates ──
    private static final int LINE_OVERHEAD = 10;
    private static final int NODE_COST = 60;
    private static final int EDGE_COST = 30;

    public static int getBudget(Perspective perspective) {
        if (perspective == null) return BUDGET_DEFAULT;
        return switch (perspective) {
            case SURVIVAL -> BUDGET_SURVIVAL;
            case TASK     -> BUDGET_TASK;
            case SOCIAL   -> BUDGET_SOCIAL;
            case CURIOUS  -> BUDGET_CURIOUS;
            case CAUTIOUS -> BUDGET_CAUTIOUS;
        };
    }

    public static boolean matchesCategory(Perspective perspective, String reflexId, String category) {
        if (perspective == null) return true;
        Set<String> prefixes = CATEGORY_PREFIXES.get(perspective);
        if (prefixes == null || prefixes.isEmpty()) return true;
        String key = category != null ? category : reflexId;
        return prefixes.stream().anyMatch(key::startsWith);
    }

    public static List<ReflexGraph.ReflexGraphNode> filterByPerspective(
            Perspective perspective, Collection<ReflexGraph.ReflexGraphNode> nodes, String goalContext) {
        List<ReflexGraph.ReflexGraphNode> filtered = new ArrayList<>();
        for (ReflexGraph.ReflexGraphNode n : nodes) {
            if (!matchesCategory(perspective, n.reflexId(), n.category)) continue;
            if (perspective == Perspective.TASK && goalContext != null && !goalContext.isEmpty()) {
                String lowerGoal = goalContext.toLowerCase();
                String lowerId = n.reflexId().toLowerCase();
                String lowerCat = n.category != null ? n.category.toLowerCase() : "";
                boolean goalMatch = lowerCat.contains(lowerGoal) || lowerGoal.contains(lowerCat)
                        || lowerId.contains(lowerGoal.replace(" ", "_"));
                if (!goalMatch) continue;
            }
            filtered.add(n);
        }
        return filtered;
    }

    public static String compileReflexSummary(
            Perspective perspective, Collection<ReflexGraph.ReflexGraphNode> nodes,
            List<ReflexGraph.ReflexGraphEdge> edges, String goalContext) {
        int budget = getBudget(perspective);
        List<ReflexGraph.ReflexGraphNode> filtered = filterByPerspective(perspective, nodes, goalContext);
        filtered.sort((a, b) -> Double.compare(b.successRate(), a.successRate()));

        StringBuilder sb = new StringBuilder();
        int used = 0;
        int budgetMargin = (int) (budget * 0.85);

        List<ReflexGraph.ReflexGraphEdge> filteredEdges = edges.stream()
                .filter(e -> filtered.stream().anyMatch(n -> n.reflexId().equals(e.fromId()))
                        || filtered.stream().anyMatch(n -> n.reflexId().equals(e.toId())))
                .collect(Collectors.toList());

        for (ReflexGraph.ReflexGraphNode n : filtered) {
            String line = n.toCompactSummary();
            int cost = NODE_COST + line.length() / 2;
            if (used + cost > budgetMargin) break;
            sb.append(line).append("\n");
            used += cost;

            for (ReflexGraph.ReflexGraphEdge e : filteredEdges) {
                if (!e.fromId().equals(n.reflexId()) && !e.toId().equals(n.reflexId())) continue;
                String edgeLine = String.format("  └─[%s]→%s (p=%.2f)", e.type(), e.toId(), e.posteriorMean());
                int edgeCost = EDGE_COST + edgeLine.length() / 2;
                if (used + edgeCost > budget) break;
                sb.append(edgeLine).append("\n");
                used += edgeCost;
            }
        }
        sb.append(String.format("[预算:%d/%d tokens| 过滤后%d个反射]", used, budget, filtered.size()));
        return sb.toString();
    }
}
