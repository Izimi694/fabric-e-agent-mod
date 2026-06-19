package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportTest {

    @Test
    @DisplayName("empty report has safe defaults")
    void empty() {
        var r = PerformanceReport.empty();
        assertEquals(0, r.resourceTrends().size());
        assertEquals(1.0, r.vitalSigns().health());
        assertEquals(0, r.ticksSinceLastLLM());
    }

    @Test
    @DisplayName("VitalSigns clamps to [0, 1]")
    void vitalsClamp() {
        var vs = new PerformanceReport.VitalSigns(-0.5, 1.5, 0.5);
        assertEquals(0.0, vs.health());
        assertEquals(1.0, vs.hunger());
        assertEquals(0.5, vs.armor());
    }

    @Test
    @DisplayName("ResourceTrend clamps count")
    void resourceTrendClamps() {
        var t = new PerformanceReport.ResourceTrend("iron", -5, 0, 300);
        assertEquals(0, t.count());
    }

    @Test
    @DisplayName("early warning triggers on resource crash")
    void earlyWarningResource() {
        var report = new PerformanceReport(
            List.of(new PerformanceReport.ResourceTrend("iron", 0, -4, 300)),
            List.of(),
            new PerformanceReport.VitalSigns(1.0, 1.0, 1.0),
            List.of(),
            new PerformanceReport.EnvironmentStats(0.5, 20),
            List.of(),
            3000 // ticksSinceLastLLM > cooldown
        );
        assertTrue(report.requiresEarlyLLM());
    }

    @Test
    @DisplayName("early warning triggers on failure spike")
    void earlyWarningFailure() {
        var report = new PerformanceReport(
            List.of(),
            List.of(new PerformanceReport.FailureTrend("mining", 0.7, 0.2)),
            new PerformanceReport.VitalSigns(1.0, 1.0, 1.0),
            List.of(),
            new PerformanceReport.EnvironmentStats(0.5, 20),
            List.of(),
            3000
        );
        assertTrue(report.requiresEarlyLLM());
    }

    @Test
    @DisplayName("no early warning when cooldown not elapsed")
    void earlyWarningCooldown() {
        var report = new PerformanceReport(
            List.of(new PerformanceReport.ResourceTrend("iron", 0, -4, 300)),
            List.of(),
            new PerformanceReport.VitalSigns(1.0, 1.0, 1.0),
            List.of(),
            new PerformanceReport.EnvironmentStats(0.5, 20),
            List.of(),
            1000 // < cooldown
        );
        assertFalse(report.requiresEarlyLLM());
    }
}
