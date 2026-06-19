package com.izimi.eagent.brainstem.perception;

import com.izimi.eagent.brainstem.action.GoalAdoption;
import com.izimi.eagent.brainstem.action.LandscapePatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SalienceMapTest {

    @TempDir
    Path tempDir;

    private SalienceMap freshMap() { return new SalienceMap(new ValueRegistry(tempDir)); }

    @Test @DisplayName("getBaseSalience returns hardcoded value for known blocks")
    void knownBlock() {
        var sm = freshMap();
        assertEquals(1.0f, sm.getBaseSalience("diamond_ore"));
        assertEquals(0.6f, sm.getBaseSalience("iron_ore"));
    }

    @Test @DisplayName("getBaseSalience returns curiosity for unknown blocks")
    void unknownBlock() {
        var sm = freshMap();
        assertEquals(SalienceMap.UNKNOWN_BLOCK_BASE_SALIENCE, sm.getBaseSalience("ancient_debris"));
    }

    @Test @DisplayName("getBaseSalience returns learned value after registry learns")
    void learnedBlock() {
        var vr = new ValueRegistry(tempDir);
        vr.learnValue("ancient_debris", 0.9);
        assertEquals(0.9f, new SalienceMap(vr).getBaseSalience("ancient_debris"));
    }

    @Test @DisplayName("expandSemanticMatch finds matching block IDs")
    void expandSemanticMatch() {
        var sm = freshMap();
        var visible = List.of("minecraft:stone", "minecraft:ancient_debris", "minecraft:dirt");
        var result = sm.expandSemanticMatch(List.of("ancient_debris"), visible);
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("ancient_debris"));
    }

    @Test @DisplayName("expandSemanticMatch returns empty for null")
    void expandNull() {
        assertTrue(freshMap().expandSemanticMatch(null, List.of("stone")).isEmpty());
    }

    @Test @DisplayName("applyPatch adds tracked boost")
    void applyPatchAddsBoost() {
        var sm = freshMap();
        var attr = LandscapePatch.Attractor.ofType("iron_ore", 0.5);
        sm.applyPatch(LandscapePatch.attractOnly(attr));
        assertEquals(1, sm.activeBoostCount());
    }

    @Test @DisplayName("tick decays boost over time")
    void tickDecays() {
        var sm = freshMap();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        float after0 = sm.getSalience("iron_ore", 10);
        for (int i = 0; i < 100; i++) sm.tick(Map.of("iron_ore", 10.0f));
        float after100 = sm.getSalience("iron_ore", 10);
        assertTrue(after100 < after0);
    }

    @Test @DisplayName("tick removes expired boost")
    void tickRemovesExpired() {
        var sm = freshMap();
        sm.applyAttractor(new LandscapePatch.Attractor("iron_ore", null, null, 0.001, 0.05, 0));
        int ticks = 250;
        for (int i = 0; i < ticks; i++) sm.tick(Map.of());
        assertEquals(0, sm.activeBoostCount());
    }

    @Test @DisplayName("getSalience incorporates distance step function")
    void salienceWithDistance() {
        var sm = freshMap();
        float far = sm.getSalience("iron_ore", 20);
        float near = sm.getSalience("iron_ore", 2);
        assertTrue(near > far);
        assertEquals(0, far);
    }

    @Test @DisplayName("getCandidates returns sorted by salience descending")
    void candidatesSorted() {
        var sm = freshMap();
        var blocks = Map.of("diamond_ore", 5.0f, "dirt", 3.0f);
        var candidates = sm.getCandidates(blocks);
        assertEquals(2, candidates.size());
        assertEquals("diamond_ore", candidates.get(0).targetType());
        assertEquals("dirt", candidates.get(1).targetType());
    }

    @Test @DisplayName("getCandidates filters zero-salience blocks")
    void candidatesFilterZero() {
        var sm = freshMap();
        var blocks = Map.<String, Float>of();
        assertTrue(sm.getCandidates(blocks).isEmpty());
    }

    @Test @DisplayName("applyRepulsor reduces salience")
    void applyRepulsor() {
        var sm = freshMap();
        var rep = new LandscapePatch.Repulsor("creeper", null, 0.5, 0.05, 200);
        sm.applyRepulsor(rep);
        assertEquals(1, sm.activeBoostCount());
    }

    @Test @DisplayName("goalAdoption commitScore increases when candidate is top")
    void goalAdoptionCommitIncreases() {
        var sm = freshMap();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        double before = sm.getGoalAdoptions().get(0).commitScore();
        for (int i = 0; i < 80; i++) {
            sm.tick(Map.of("iron_ore", 1.0f), "iron_ore");
        }
        double after = sm.getGoalAdoptions().get(0).commitScore();
        assertTrue(after > before);
        assertTrue(sm.getGoalAdoptions().get(0).isAdopted());
    }

    @Test @DisplayName("goalAdoption commitScore decays when candidate is not top")
    void goalAdoptionCommitDecays() {
        var sm = freshMap();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        double before = sm.getGoalAdoptions().get(0).commitScore();
        for (int i = 0; i < 1000; i++) {
            sm.tick(Map.of("iron_ore", 1.0f), "stone");
        }
        double after = sm.getGoalAdoptions().get(0).commitScore();
        assertTrue(after < before);
    }

    @Test @DisplayName("goalAdoption forgotten when commitScore drops below threshold")
    void goalAdoptionForgotten() {
        var sm = freshMap();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        for (int i = 0; i < 12000; i++) {
            sm.tick(Map.of("iron_ore", 1.0f), "stone");
        }
        assertTrue(sm.getGoalAdoptions().stream()
            .noneMatch(g -> "iron_ore".equals(g.attractorType())));
    }

    @Test @DisplayName("resetGoalAdoptions clears tracked adoptions")
    void resetAdoptions() {
        var sm = freshMap();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        sm.tick(Map.of());
        assertFalse(sm.getGoalAdoptions().isEmpty());
        sm.resetGoalAdoptions();
        assertTrue(sm.getGoalAdoptions().isEmpty());
    }

    @Test @DisplayName("getCandidates includes category from boostCategories")
    void candidateWithCategory() {
        var sm = freshMap();
        var attr = new LandscapePatch.Attractor(null, "ore", null, 0.5, 0.005, 600);
        sm.applyAttractor(attr);
        var candidates = sm.getCandidates(Map.of("iron_ore", 5.0f));
        assertFalse(candidates.isEmpty());
        var iron = candidates.stream().filter(c -> "iron_ore".equals(c.targetType())).findFirst();
        assertTrue(iron.isPresent());
        assertEquals("ore", iron.get().category());
    }
}
