package com.izimi.eagent.hormonal;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.izimi.eagent.cortex.api.HormonalPreset;

public class HormonalSystem {

    // ── Old decay constants (backward compat, Phase 3 deprecation target) ──
    private static final double STRESS_DECAY = 0.001;
    private static final double AGGRESSION_DECAY = 0.002;
    private static final double CURIOSITY_DECAY_PER_TICK = 0.000019;
    private static final double INTIMACY_DECAY_PER_TICK = 0.0001;
    private static final double CLAMP_MIN = 0.0;
    private static final double CLAMP_MAX = 1.0;
    private static final double NOVELTY_BOOST = 0.3;
    private static final double NOVELTY_CAP = 0.95;
    private static final double FAMILIAR_SUPPRESS = 0.95;
    private static final double STRESS_SUPPRESS_FACTOR = 1.5;
    private static final double CURIOSITY_FLOOR = 0.1;

    // ── New 4D decay constants ──
    private static final double NE_DECAY_PER_TICK = 0.005;
    private static final double DA_DECAY_PER_TICK = 0.002;
    private static final double SEROTONIN_DECAY_PER_TICK = 0.0005;
    private static final double ACH_DECAY_PER_TICK = 0.002;

    // ── Old fields (backward compat) ──
    private double stress;
    private double aggression;
    private double curiosity;
    private final Map<UUID, Double> intimacy = new HashMap<>();

    // ── New 4D fields ──
    private double ne;
    private double da;
    private double serotonin;
    private double ach;

    public HormonalSystem() {
        this.stress = 0.1;
        this.aggression = 0.2;
        this.curiosity = 0.3;
        this.ne = 0.1;
        this.da = 0.2;
        this.serotonin = 0.3;
        this.ach = 0.3;
    }

    public HormonalSystem(double initialStress, double initialAggression, double initialCuriosity) {
        this.stress = clamp(initialStress);
        this.aggression = clamp(initialAggression);
        this.curiosity = clamp(initialCuriosity);
        this.ne = clamp(initialStress);
        this.da = clamp(initialAggression);
        this.serotonin = clamp(initialCuriosity * 0.6 + 0.1);
        this.ach = 0.3;
    }

    /** Bulk-set all hormone levels from a preset (playstyle switch) */
    public void applyPreset(HormonalPreset preset) {
        this.stress = clamp(preset.stress());
        this.aggression = clamp(preset.aggression());
        this.curiosity = clamp(preset.curiosity());
        this.ne = clamp(preset.ne());
        this.da = clamp(preset.da());
        this.serotonin = clamp(preset.serotonin());
        this.ach = clamp(preset.ach());
    }

    // ── Event triggers (update both old + new) ──

    public void onTakeDamage() {
        stress = clamp(stress + 0.1);
        ne = clamp(ne + 0.12);
        serotonin = clamp(serotonin - 0.02);
    }

    public void onCombatWin() {
        aggression = clamp(aggression + 0.05);
        stress = max(stress - 0.02, 0);
        da = clamp(da + 0.1);
        ach = clamp(ach + 0.1);
        ne = max(ne - 0.05, 0);
    }

    public void onCombatLoss() {
        stress = clamp(stress + 0.15);
        aggression = clamp(aggression - 0.1);
        ne = clamp(ne + 0.15);
        serotonin = clamp(serotonin + 0.05);
        da = clamp(da - 0.08);
    }

    public void onNovelDiscovery() {
        curiosity = Math.min(NOVELTY_CAP, curiosity + NOVELTY_BOOST);
        da = clamp(da + 0.12);
        ach = clamp(ach - 0.1);
    }

    public void onEnterNewBiome() {
        curiosity = Math.min(NOVELTY_CAP, curiosity + 0.2);
        ach = clamp(ach - 0.15);
    }

    public void onTaskSuccess() {
        stress = max(stress - 0.02, 0);
        da = clamp(da + 0.06);
        ne = max(ne - 0.04, 0);
    }

    public void onPlayerPraise(UUID playerId) {
        intimacy.merge(playerId, 0.05, Math::min);
        stress = max(stress - 0.02, 0);
        serotonin = clamp(serotonin + 0.06);
        ne = max(ne - 0.03, 0);
    }

    public void onPlayerCriticize(UUID playerId) {
        intimacy.merge(playerId, -0.03, Double::sum);
        intimacy.put(playerId, max(intimacy.getOrDefault(playerId, 0.5), 0.0));
        stress = clamp(stress + 0.03);
        serotonin = clamp(serotonin - 0.04);
        ne = clamp(ne + 0.05);
    }

    public void onPlayerNearby(UUID playerId) {
        intimacy.putIfAbsent(playerId, 0.5);
    }

    public double getIntimacy(UUID playerId) {
        return intimacy.getOrDefault(playerId, 0.5);
    }

    // ── Tick: decay old fields + 4D fields ──

    public void tick() {
        stress     = max(stress - STRESS_DECAY, 0);
        aggression = max(aggression - AGGRESSION_DECAY, 0);
        curiosity  = curiosity * (1 - CURIOSITY_DECAY_PER_TICK);
        curiosity  = Math.max(CURIOSITY_FLOOR, Math.min(NOVELTY_CAP, curiosity));

        ne = max(ne - NE_DECAY_PER_TICK, 0);
        da = max(da - DA_DECAY_PER_TICK, 0);
        serotonin = max(serotonin - SEROTONIN_DECAY_PER_TICK, 0);
        ach = max(ach - ACH_DECAY_PER_TICK, 0);

        // Intimacy decay per tick
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : intimacy.entrySet()) {
            double newVal = max(entry.getValue() - INTIMACY_DECAY_PER_TICK, 0);
            if (newVal <= 0) {
                toRemove.add(entry.getKey());
            } else {
                entry.setValue(newVal);
            }
        }
        toRemove.forEach(intimacy::remove);
    }

    public void tickWithFamiliarity(boolean familiar) {
        tick();
        if (familiar) {
            curiosity = Math.max(CURIOSITY_FLOOR, curiosity * FAMILIAR_SUPPRESS);
        }
    }

    public double getCuriosityThreshold(double beta, double stress) {
        double threshold = 0.5 + beta * 10;
        if (stress > 0.5) threshold *= STRESS_SUPPRESS_FACTOR;
        return Math.min(1.0, threshold);
    }

    // ── Old getters (backward compat) ──

    public double getStress()     { return stress; }
    public double getAggression() { return aggression; }
    public double getCuriosity()  { return curiosity; }
    public Map<UUID, Double> getIntimacyMap() { return new HashMap<>(intimacy); }

    // ── New 4D getters ──

    public double getNE()         { return ne; }
    public double getDA()         { return da; }
    public double getSerotonin()  { return serotonin; }
    public double getACh()        { return ach; }

    public NeuroState getNeuroState() {
        return new NeuroState(ne, da, serotonin, ach);
    }

    /**
     * 生成候选反射ID集 (粗筛, 不参与排序).
     * 基于激素状态筛选相关范畴: stress高→生存, aggression高→战斗, curiosity高→探索.
     */
    public List<String> getCandidateCategories() {
        List<String> categories = new ArrayList<>();
        if (stress > 0.6) categories.add("flee");
        if (stress > 0.4) categories.add("survival");
        if (aggression > 0.6) categories.add("attack");
        if (curiosity > 0.5) categories.add("explore");
        if (stress < 0.3 && aggression < 0.4) categories.add("routine");
        if (categories.isEmpty()) categories.add("routine");
        return categories;
    }

    public String summary() {
        return String.format("stress=%.2f aggression=%.2f curiosity=%.2f ne=%.2f da=%.2f 5ht=%.2f ach=%.2f intimacy=%d players",
                stress, aggression, curiosity, ne, da, serotonin, ach, intimacy.size());
    }

    private static double clamp(double val) {
        return Math.max(CLAMP_MIN, Math.min(CLAMP_MAX, val));
    }

    private static double max(double a, double b) {
        return Math.max(a, b);
    }

    public HormonalState snapshot() {
        return new HormonalState(stress, aggression, curiosity, new HashMap<>(intimacy));
    }

    public record HormonalState(double stress, double aggression, double curiosity, Map<UUID, Double> intimacy) {}
}
