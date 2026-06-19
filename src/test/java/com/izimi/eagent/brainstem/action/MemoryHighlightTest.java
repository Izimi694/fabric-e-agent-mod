package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryHighlightTest {

    @Test
    @DisplayName("constructor clamps salience and ticksAgo")
    void clamps() {
        var h = new MemoryHighlight("DEATH", "fell into lava", 1.5, -10);
        assertEquals(1.0, h.salience());
        assertEquals(0, h.ticksAgo());
    }

    @Test
    @DisplayName("stores values correctly")
    void stores() {
        var h = new MemoryHighlight("FIRST_DIAMOND", "found first diamond", 0.9, 500);
        assertEquals("FIRST_DIAMOND", h.eventType());
        assertEquals("found first diamond", h.summary());
        assertEquals(0.9, h.salience());
        assertEquals(500, h.ticksAgo());
    }
}
