package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallContextTest {

    @Test
    @DisplayName("empty context has safe defaults")
    void empty() {
        var ctx = CallContext.empty();
        assertEquals("", ctx.personaProfile());
        assertEquals("ROUTINE", ctx.state().currentPerspective());
    }

    @Test
    @DisplayName("reportTokenUsage estimates non-negative")
    void tokenUsage() {
        var ctx = CallContext.empty();
        assertTrue(ctx.reportTokenUsage() >= 0);
    }

    @Test
    @DisplayName("does not exceed token budget by default")
    void underBudget() {
        var ctx = CallContext.empty();
        assertFalse(ctx.exceedsTokenBudget());
    }

    @Test
    @DisplayName("MAX_TOKENS is set to 1024")
    void maxTokens() {
        assertEquals(1024, CallContext.MAX_TOKENS);
    }
}
