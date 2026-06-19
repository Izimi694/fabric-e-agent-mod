package com.izimi.eagent.brainstem.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ValueRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("default getValue returns 0 for unknown blocks")
    void unknownBlock() {
        var r = new ValueRegistry(tempDir);
        assertEquals(0.0, r.getValue("ancient_debris"));
        assertFalse(r.hasLearned("ancient_debris"));
    }

    @Test
    @DisplayName("learnValue stores and persists to disk")
    void learnAndPersist() {
        var r = new ValueRegistry(tempDir);
        r.learnValue("ancient_debris", 0.9);
        assertEquals(0.9, r.getValue("ancient_debris"));
        assertTrue(r.hasLearned("ancient_debris"));

        var r2 = new ValueRegistry(tempDir);
        r2.load();
        assertEquals(0.9, r2.getValue("ancient_debris"));
    }

    @Test
    @DisplayName("learnBlockIfUnknown only learns unknown blocks with high satisfaction")
    void learnIfUnknown() {
        var r = new ValueRegistry(tempDir);

        r.learnBlockIfUnknown("ancient_debris", 0.9);
        assertTrue(r.hasLearned("ancient_debris"));
        assertEquals(SalienceMap.UNKNOWN_BLOCK_BASE_SALIENCE + 0.3, r.getValue("ancient_debris"), 0.001);

        double firstLearned = r.getValue("ancient_debris");
        r.learnBlockIfUnknown("ancient_debris", 0.9);
        assertEquals(firstLearned, r.getValue("ancient_debris"));
    }

    @Test
    @DisplayName("learnBlockIfUnknown ignores low satisfaction")
    void ignoreLowSatisfaction() {
        var r = new ValueRegistry(tempDir);
        r.learnBlockIfUnknown("ancient_debris", 0.5);
        assertFalse(r.hasLearned("ancient_debris"));
    }

    @Test
    @DisplayName("case insensitive key matching")
    void caseInsensitive() {
        var r = new ValueRegistry(tempDir);
        r.learnValue("ANCIENT_DEBRIS", 0.9);
        assertTrue(r.hasLearned("ancient_debris"));
        assertEquals(0.9, r.getValue("Ancient_Debris"));
    }

    @Test
    @DisplayName("clear removes all learned values")
    void clear() {
        var r = new ValueRegistry(tempDir);
        r.learnValue("diamond_ore", 1.0);
        assertEquals(1, r.learnedCount());
        r.clear();
        assertEquals(0, r.learnedCount());
    }
}
