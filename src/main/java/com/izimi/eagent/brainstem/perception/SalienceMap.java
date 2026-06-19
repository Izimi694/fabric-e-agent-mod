package com.izimi.eagent.brainstem.perception;

import com.izimi.eagent.brainstem.action.GoalAdoption;
import com.izimi.eagent.brainstem.action.LandscapePatch;
import com.izimi.eagent.util.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SalienceMap {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public static final float UNKNOWN_BLOCK_BASE_SALIENCE = 0.15f;
    public static final float INTERACTABLE_BONUS = 0.1f;
    public static final double MAX_DISTANCE = 16.0;
    public static final double COMMIT_SCORE_FORGOTTEN = 0.001;

    public record Candidate(String targetType, float salience, String source, String category) {
        public Candidate(String targetType, float salience, String source) {
            this(targetType, salience, source, null);
        }
    }
    public record TrackedBoost(String targetType, TaskBoost boost, int elapsedTicks) {
        public double currentValue() { return boost.currentBoost(elapsedTicks); }
        public boolean isExpired() { return boost.isExpired(elapsedTicks); }
    }

    private final ValueRegistry valueRegistry;
    private final TagResolver tagResolver;
    private final Map<String, Float> hardcodedTable = new HashMap<>();
    private final Map<String, TrackedBoost> activeBoosts = new LinkedHashMap<>();
    private final Map<String, GoalAdoption> goalAdoptions = new LinkedHashMap<>();
    private final Map<String, String> boostCategories = new HashMap<>();

    public SalienceMap(ValueRegistry valueRegistry) {
        this(valueRegistry, null);
    }

    public SalienceMap(ValueRegistry valueRegistry, TagResolver tagResolver) {
        this.valueRegistry = valueRegistry;
        this.tagResolver = tagResolver;
        initHardcodedTable();
    }

    private void initHardcodedTable() {
        hardcodedTable.put("diamond_ore", 1.0f);
        hardcodedTable.put("iron_ore", 0.6f);
        hardcodedTable.put("coal_ore", 0.4f);
        hardcodedTable.put("copper_ore", 0.3f);
        hardcodedTable.put("gold_ore", 0.5f);
        hardcodedTable.put("oak_log", 0.2f);
        hardcodedTable.put("birch_log", 0.2f);
        hardcodedTable.put("spruce_log", 0.2f);
        hardcodedTable.put("stone", 0.1f);
        hardcodedTable.put("cobblestone", 0.1f);
        hardcodedTable.put("dirt", 0.05f);
        hardcodedTable.put("grass_block", 0.05f);
    }

    public float getBaseSalience(String blockId) {
        if (blockId == null) return 0;
        String key = blockId.toLowerCase();
        if (hardcodedTable.containsKey(key)) return hardcodedTable.get(key);
        double learned = valueRegistry.getValue(key);
        if (learned > 0) return (float) learned;
        return UNKNOWN_BLOCK_BASE_SALIENCE;
    }

    public void applyPatch(LandscapePatch patch) {
        if (patch.attractor() != null) applyAttractor(patch.attractor());
        if (patch.repulsor() != null) applyRepulsor(patch.repulsor());
    }

    public void applyAttractor(LandscapePatch.Attractor attr) {
        List<String> targets = new ArrayList<>();
        if (attr.type() != null) { targets.add(attr.type()); }
        else if (attr.category() != null) { targets.add(attr.category()); }
        else if (attr.isSemantic()) { targets.addAll(attr.semanticLabels()); }

        for (String t : targets) {
            String key = t.toLowerCase();
            if (!activeBoosts.containsKey(key) || activeBoosts.get(key).boost().startBoost() < attr.salienceBoost()) {
                activeBoosts.put(key, new TrackedBoost(key,
                    new TaskBoost(key, attr.salienceBoost(), attr.decayRate(), attr.maxTicks()), 0));
                goalAdoptions.put(key, GoalAdoption.pending(key, attr.salienceBoost()));
            }
            if (attr.category() != null) {
                boostCategories.put(key, attr.category());
            }
        }
    }

    public void applyRepulsor(LandscapePatch.Repulsor rep) {
        String key = rep.type() != null ? rep.type().toLowerCase() : rep.category().toLowerCase();
        TaskBoost repBoost = new TaskBoost(key, -rep.salienceReduction(), rep.decayRate(), rep.maxTicks());
        activeBoosts.put(key, new TrackedBoost(key, repBoost, 0));
    }

    public void tick(Map<String, Float> visibleBlocks) {
        tick(visibleBlocks, null);
    }

    public void tick(Map<String, Float> visibleBlocks, String actionSorterTopCandidate) {
        List<String> expired = new ArrayList<>();
        for (var entry : activeBoosts.entrySet()) {
            String key = entry.getKey();
            TrackedBoost tb = entry.getValue();
            TrackedBoost updated = new TrackedBoost(key, tb.boost(), tb.elapsedTicks() + 1);
            activeBoosts.put(key, updated);
            if (updated.isExpired()) { expired.add(key); }
        }
        for (String k : expired) {
            activeBoosts.remove(k);
            boostCategories.remove(k);
        }

        var adoptIter = goalAdoptions.entrySet().iterator();
        while (adoptIter.hasNext()) {
            var entry = adoptIter.next();
            String key = entry.getKey();
            GoalAdoption ga = entry.getValue();
            boolean isTop = actionSorterTopCandidate != null
                && actionSorterTopCandidate.toLowerCase().contains(key);
            GoalAdoption updated = isTop ? ga.withCommitIncrease() : ga.withCommitDecay();
            if (updated.commitScore() < COMMIT_SCORE_FORGOTTEN) {
                adoptIter.remove();
            } else {
                entry.setValue(updated);
            }
        }
    }

    public float getSalience(String blockId, double distance) {
        float base = getBaseSalience(blockId);
        for (var tb : activeBoosts.values()) {
            if (blockId.toLowerCase().contains(tb.targetType())) {
                base += tb.currentValue();
            }
        }
        double distFactor = distance <= MAX_DISTANCE ? 1.0 : 0.0;
        return (float) Math.max(0, Math.min(1, base * distFactor));
    }

    private String findCategory(String blockId) {
        String lower = blockId.toLowerCase();
        String exact = boostCategories.get(lower);
        if (exact != null) return exact;
        for (var entry : boostCategories.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) return entry.getValue();
        }
        return null;
    }

    public List<Candidate> getCandidates(Map<String, Float> visibleBlocksWithDistance) {
        List<Candidate> result = visibleBlocksWithDistance.entrySet().stream()
            .map(e -> new Candidate(e.getKey(), getSalience(e.getKey(), e.getValue()), "SalienceMap",
                findCategory(e.getKey())))
            .filter(c -> c.salience() > 0.01f)
            .sorted(Comparator.comparingDouble(Candidate::salience).reversed())
            .collect(Collectors.toList());
        if (LOGGER.isInfoEnabled() && !result.isEmpty()) {
            var top3 = result.stream().limit(3)
                .map(c -> c.targetType() + "=" + String.format("%.3f", c.salience()))
                .collect(Collectors.joining(", "));
            LOGGER.info("[SM] {} candidates: {}", result.size(), top3);
        }
        return result;
    }

    public List<GoalAdoption> getGoalAdoptions() {
        return List.copyOf(goalAdoptions.values());
    }

    public void resetGoalAdoptions() {
        goalAdoptions.clear();
    }

    public List<String> expandSemanticMatch(List<String> semanticLabels, List<String> visibleBlockIds) {
        if (semanticLabels == null || semanticLabels.isEmpty()) return List.of();
        Set<String> matched = new HashSet<>();
        for (String label : semanticLabels) {
            String lower = label.toLowerCase();
            for (String blockId : visibleBlockIds) {
                if (blockId.toLowerCase().contains(lower)) matched.add(blockId);
            }
            if (tagResolver != null) matched.addAll(tagResolver.expandCategory(lower));
        }
        List<String> result = new ArrayList<>(matched);
        Collections.sort(result);
        return result;
    }

    public ValueRegistry getValueRegistry() { return valueRegistry; }
    public int hardcodedCount() { return hardcodedTable.size(); }
    public int activeBoostCount() { return activeBoosts.size(); }
}
