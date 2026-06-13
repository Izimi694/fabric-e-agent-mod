package com.izimi.eagent.hormonal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeuroStateTest {

    @Test
    @DisplayName("constructor clamps values to [0, 1]")
    void clampsValues() {
        var s = new NeuroState(-0.5, 1.5, 0.3, 2.0);
        assertEquals(0.0, s.ne());
        assertEquals(1.0, s.da());
        assertEquals(0.3, s.serotonin());
        assertEquals(1.0, s.ach());
    }

    @Test
    @DisplayName("neutral has all values at 0.3")
    void neutral() {
        var s = NeuroState.neutral();
        assertEquals(0.3, s.ne());
        assertEquals(0.3, s.da());
        assertEquals(0.3, s.serotonin());
        assertEquals(0.3, s.ach());
    }

    @Test
    @DisplayName("zero has all values at 0")
    void zero() {
        var s = NeuroState.zero();
        assertEquals(0, s.ne());
        assertEquals(0, s.da());
        assertEquals(0, s.serotonin());
        assertEquals(0, s.ach());
    }

    @Test
    @DisplayName("cosineSimilarity returns 1 for identical vectors")
    void cosineSame() {
        var s = new NeuroState(0.7, 0.6, 0.2, 0.8);
        assertEquals(1.0, s.cosineSimilarity(s), 0.001);
    }

    @Test
    @DisplayName("cosineSimilarity returns 0 for orthogonal vectors")
    void cosineOrthogonal() {
        var a = new NeuroState(1, 0, 0, 0);
        var b = new NeuroState(0, 1, 0, 0);
        assertEquals(0.0, a.cosineSimilarity(b), 0.001);
    }

    @Test
    @DisplayName("cosineSimilarity returns 0 for zero vector")
    void cosineZero() {
        var z = NeuroState.zero();
        var a = new NeuroState(0.7, 0.6, 0.2, 0.8);
        assertEquals(0.0, z.cosineSimilarity(a));
        assertEquals(0.0, a.cosineSimilarity(z));
    }

    @Test
    @DisplayName("cosineSimilarity: 4D ATTACK vs EXPLORE < 3D distance")
    void cosine4DBetterThan3D() {
        var attack = new NeuroState(0.7, 0.6, 0.2, 0.8);
        var explore = new NeuroState(0.2, 0.7, 0.7, 0.2);
        double cos4d = attack.cosineSimilarity(explore);
        double cos3d = cosine3D(attack, explore);
        assertTrue(cos4d < cos3d, "4D should be more discriminative: 4D=" + cos4d + " 3D=" + cos3d);
    }

    private double cosine3D(NeuroState a, NeuroState b) {
        double dot = a.ne() * b.ne() + a.da() * b.da() + a.serotonin() * b.serotonin();
        double mag1 = Math.sqrt(a.ne() * a.ne() + a.da() * a.da() + a.serotonin() * a.serotonin());
        double mag2 = Math.sqrt(b.ne() * b.ne() + b.da() * b.da() + b.serotonin() * b.serotonin());
        return mag1 == 0 || mag2 == 0 ? 0 : dot / (mag1 * mag2);
    }

    @Test
    @DisplayName("getValue returns correct field by name")
    void getValue() {
        var s = new NeuroState(0.1, 0.2, 0.3, 0.4);
        assertEquals(0.1, s.getValue("ne"));
        assertEquals(0.2, s.getValue("da"));
        assertEquals(0.3, s.getValue("serotonin"));
        assertEquals(0.4, s.getValue("ach"));
        assertEquals(0.3, s.getValue("5ht"));
        assertEquals(0.4, s.getValue("acetylcholine"));
    }

    @Test
    @DisplayName("getValue throws for unknown field")
    void getValueUnknown() {
        var s = NeuroState.neutral();
        assertThrows(IllegalArgumentException.class, () -> s.getValue("unknown"));
    }

    @Test
    @DisplayName("with* methods return new instances")
    void withMethods() {
        var s = NeuroState.neutral();
        assertEquals(0.8, s.withNE(0.8).ne(), 0.001);
        assertEquals(0.7, s.withDA(0.7).da(), 0.001);
        assertEquals(0.1, s.withSerotonin(0.1).serotonin(), 0.001);
        assertEquals(0.9, s.withACh(0.9).ach(), 0.001);
        assertNotSame(s, s.withNE(0.5));
    }

    @Test
    @DisplayName("record equals/hashCode by value")
    void recordEquality() {
        var a = new NeuroState(0.1, 0.2, 0.3, 0.4);
        var b = new NeuroState(0.1, 0.2, 0.3, 0.4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
