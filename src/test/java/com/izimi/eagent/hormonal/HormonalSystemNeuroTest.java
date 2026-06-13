package com.izimi.eagent.hormonal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HormonalSystemNeuroTest {

    @Test
    @DisplayName("default constructor initializes 4D fields")
    void defaultConstructor() {
        var h = new HormonalSystem();
        assertEquals(0.1, h.getNE());
        assertEquals(0.2, h.getDA());
        assertEquals(0.3, h.getSerotonin());
        assertEquals(0.3, h.getACh());
    }

    @Test
    @DisplayName("old 3-arg constructor also initializes 4D fields")
    void oldConstructorInit4D() {
        var h = new HormonalSystem(0.5, 0.6, 0.7);
        assertEquals(0.5, h.getNE());
        assertEquals(0.6, h.getDA());
        assertEquals(0.7 * 0.6 + 0.1, h.getSerotonin(), 0.001);
        assertEquals(0.3, h.getACh());
    }

    @Test
    @DisplayName("onTakeDamage updates NE and serotonin")
    void takeDamageUpdates4D() {
        var h = new HormonalSystem();
        h.onTakeDamage();
        assertEquals(0.22, h.getNE(), 0.001);
        assertEquals(0.28, h.getSerotonin(), 0.001);
    }

    @Test
    @DisplayName("onTakeDamage preserves backward compat")
    void takeDamageBackward() {
        var h = new HormonalSystem();
        h.onTakeDamage();
        assertEquals(0.2, h.getStress(), 0.001);
    }

    @Test
    @DisplayName("onCombatWin updates DA, ACh, NE")
    void combatWinUpdates4D() {
        var h = new HormonalSystem(0.3, 0.2, 0.3);
        h.onCombatWin();
        assertEquals(0.3, h.getDA(), 0.001);
        assertEquals(0.4, h.getACh(), 0.001);
        assertEquals(0.25, h.getNE(), 0.001);
    }

    @Test
    @DisplayName("onCombatLoss updates NE, serotonin, DA")
    void combatLossUpdates4D() {
        var h = new HormonalSystem();
        h.onCombatLoss();
        assertEquals(0.25, h.getNE(), 0.001);
        assertEquals(0.35, h.getSerotonin(), 0.001);
        assertEquals(0.12, h.getDA(), 0.001);
    }

    @Test
    @DisplayName("onNovelDiscovery updates DA and ACh")
    void novelDiscoveryUpdates4D() {
        var h = new HormonalSystem();
        h.onNovelDiscovery();
        assertEquals(0.32, h.getDA(), 0.001);
        assertEquals(0.2, h.getACh(), 0.001);
    }

    @Test
    @DisplayName("onEnterNewBiome updates ACh")
    void enterNewBiomeUpdates4D() {
        var h = new HormonalSystem();
        h.onEnterNewBiome();
        assertEquals(0.15, h.getACh(), 0.001);
    }

    @Test
    @DisplayName("onTaskSuccess updates DA and NE")
    void taskSuccessUpdates4D() {
        var h = new HormonalSystem(0.3, 0.2, 0.3);
        h.onTaskSuccess();
        assertEquals(0.26, h.getDA(), 0.001);
        assertEquals(0.26, h.getNE(), 0.001);
    }

    @Test
    @DisplayName("onPlayerPraise updates serotonin and NE")
    void playerPraiseUpdates4D() {
        var h = new HormonalSystem();
        UUID pid = UUID.randomUUID();
        h.onPlayerPraise(pid);
        assertEquals(0.36, h.getSerotonin(), 0.001);
        assertEquals(0.07, h.getNE(), 0.001);
    }

    @Test
    @DisplayName("onPlayerCriticize updates serotonin and NE")
    void playerCriticizeUpdates4D() {
        var h = new HormonalSystem();
        UUID pid = UUID.randomUUID();
        h.onPlayerCriticize(pid);
        assertEquals(0.26, h.getSerotonin(), 0.001);
        assertEquals(0.15, h.getNE(), 0.001);
    }

    @Test
    @DisplayName("tick decays all 4D fields")
    void tickDecays4D() {
        var h = new HormonalSystem(0.5, 0.5, 0.5);
        h.tick();
        assertTrue(h.getNE() < 0.5);
        assertTrue(h.getDA() < 0.5);
        assertTrue(h.getSerotonin() < 0.5);
        assertTrue(h.getACh() < 0.5);
    }

    @Test
    @DisplayName("NE decays fastest among 4D fields")
    void neDecaysFastest() {
        var h = new HormonalSystem();
        for (int i = 0; i < 10; i++) {
            h.tick();
        }
        double ne = h.getNE();
        assertTrue(ne < h.getDA(),
                "NE should decay faster than DA: NE=" + ne + " DA=" + h.getDA());
        assertTrue(ne < h.getSerotonin(),
                "NE should decay faster than serotonin: NE=" + ne + " 5-HT=" + h.getSerotonin());
        assertTrue(ne < h.getACh(),
                "NE should decay faster than ACh: NE=" + ne + " ACh=" + h.getACh());
    }

    @Test
    @DisplayName("serotonin decays slowest among 4D fields")
    void serotoninDecaysSlowest() {
        var h = new HormonalSystem();
        for (int i = 0; i < 50; i++) {
            h.tick();
        }
        double s = h.getSerotonin();
        assertTrue(s > h.getNE(),
                "Serotonin should decay slower than NE: 5-HT=" + s + " NE=" + h.getNE());
        assertTrue(s > h.getDA(),
                "Serotonin should decay slower than DA: 5-HT=" + s + " DA=" + h.getDA());
        assertTrue(s > h.getACh(),
                "Serotonin should decay slower than ACh: 5-HT=" + s + " ACh=" + h.getACh());
    }

    @Test
    @DisplayName("getNeuroState returns current snapshot")
    void getNeuroState() {
        var h = new HormonalSystem(0.3, 0.4, 0.5);
        NeuroState state = h.getNeuroState();
        assertEquals(0.3, state.ne(), 0.001);
        assertEquals(0.4, state.da(), 0.001);
    }

    @Test
    @DisplayName("summary includes 4D fields")
    void summaryIncludes4D() {
        var h = new HormonalSystem();
        String s = h.summary();
        assertTrue(s.contains("ne="));
        assertTrue(s.contains("da="));
        assertTrue(s.contains("5ht="));
        assertTrue(s.contains("ach="));
    }

    @Test
    @DisplayName("old getters still work after 4D integration")
    void oldGettersBackwardCompat() {
        var h = new HormonalSystem(0.7, 0.8, 0.5);
        assertEquals(0.7, h.getStress(), 0.001);
        assertEquals(0.8, h.getAggression(), 0.001);
        assertEquals(0.5, h.getCuriosity(), 0.001);
    }
}
