package com.izimi.eagent.brainstem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericCommandTest {
    @Test @DisplayName("constructor defaults blank commandType to idle")
    void defaultsToIdle() {
        var cmd = new GenericCommand("", "test", 5, 0);
        assertEquals("idle", cmd.commandType());
    }
    @Test @DisplayName("constructor defaults null targetType to none")
    void defaultsTarget() {
        var cmd = new GenericCommand("break", null, 5, 0);
        assertEquals("none", cmd.targetType());
    }
    @Test @DisplayName("implements DomainCommand")
    void implementsDomainCommand() {
        var cmd = new GenericCommand("movement", "iron_ore", 3, 0.5);
        assertInstanceOf(DomainCommand.class, cmd);
    }
}
