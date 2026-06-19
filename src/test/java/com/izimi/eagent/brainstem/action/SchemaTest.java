package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaTest {

    @Test @DisplayName("grid coord from position")
    void gridFromCoord() {
        assertEquals(0, Schema.gridFromCoord(1.5));
        assertEquals(1, Schema.gridFromCoord(5.0));
        assertEquals(-1, Schema.gridFromCoord(-1.0));
        assertEquals(-2, Schema.gridFromCoord(-5.0));
    }

    @Test @DisplayName("gridKey format")
    void gridKey() {
        assertEquals("g:3:-2", Schema.gridKey(3, -2));
    }

    @Test @DisplayName("consecutive hi >= 3 sets permanent")
    void permanentAfter3Hi() {
        var s = new Schema("s1", "", 0, 0, 0, 0, 0.5, 10, 3, 0, false);
        assertTrue(s.permanent());
    }

    @Test @DisplayName("consecutive lo >= 2 marks removable")
    void removableAfter2Lo() {
        var s = new Schema("s1", "", 0, 0, 0, 0, 0.5, 10, 0, 2, false);
        assertTrue(s.canBeRemoved());
    }

    @Test @DisplayName("permanent schema not removable even with high lo")
    void permanentNotRemovable() {
        var s = new Schema("s1", "", 0, 0, 0, 0, 0.5, 10, 3, 2, true);
        assertFalse(s.canBeRemoved());
    }

    @Test @DisplayName("non-permanent schema with lo=1 not removable")
    void notRemovable() {
        var s = new Schema("s1", "", 0, 0, 0, 0, 0.5, 10, 1, 1, false);
        assertFalse(s.canBeRemoved());
    }

    @Test @DisplayName("null id defaults to empty")
    void nullId() {
        var s = new Schema(null, null, 0, 0, 0, 0, 0, 0, 0, 0, false);
        assertEquals("", s.id());
        assertEquals("", s.label());
    }

    @Test @DisplayName("withConsecutive increments correctly")
    void withConsecutive() {
        var s = new Schema("s1", "", 0, 0, 0, 0, 0.5, 10, 1, 0, false);
        var s2 = s.withConsecutiveHi(3);
        assertTrue(s2.permanent());
        assertEquals(3, s2.consecutiveHiCount());
    }
}
