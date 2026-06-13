package com.izimi.eagent.amygdala;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.izimi.eagent.bayesian.GatingArbiter;
import com.izimi.eagent.hormonal.HormonalSystem;

class AlarmGatingTest {

    @Test
    @DisplayName("shouldDirectConsolidate returns false when no GatingArbiter")
    void alarmNoGatingArbiter() {
        var alarm = new OneShotAlarmSystem(UUID.randomUUID());
        assertFalse(alarm.shouldDirectConsolidate("test_alarm"));
    }

    @Test
    @DisplayName("shouldDirectConsolidate returns true when controllability below gate")
    void alarmDirectConsolidate() {
        var gating = mock(GatingArbiter.class);
        when(gating.computeControllability("test", null)).thenReturn(0.2);

        var alarm = new OneShotAlarmSystem(UUID.randomUUID());
        alarm.setGatingArbiter(gating);
        assertTrue(alarm.shouldDirectConsolidate("test"));
    }

    @Test
    @DisplayName("shouldDirectConsolidate returns false when controllability above gate")
    void alarmNoDirectConsolidate() {
        var gating = mock(GatingArbiter.class);
        when(gating.computeControllability("test", null)).thenReturn(0.8);

        var alarm = new OneShotAlarmSystem(UUID.randomUUID());
        alarm.setGatingArbiter(gating);
        assertFalse(alarm.shouldDirectConsolidate("test"));
    }

    @Test
    @DisplayName("SocialObserver shouldDirectConsolidate returns false when no GatingArbiter")
    void socialNoGatingArbiter() {
        var tracker = new FamiliarityTracker();
        var observer = new SocialObserver(tracker);
        assertFalse(observer.shouldDirectConsolidate("test_reflex"));
    }

    @Test
    @DisplayName("SocialObserver shouldDirectConsolidate true when controllability low")
    void socialDirectConsolidate() {
        var gating = mock(GatingArbiter.class);
        when(gating.computeControllability("test", null)).thenReturn(0.2);

        var tracker = new FamiliarityTracker();
        var observer = new SocialObserver(tracker);
        observer.setGatingArbiter(gating);
        assertTrue(observer.shouldDirectConsolidate("test"));
    }

    @Test
    @DisplayName("SocialObserver shouldDirectConsolidate false when controllability high")
    void socialNoDirectConsolidate() {
        var gating = mock(GatingArbiter.class);
        when(gating.computeControllability("test", null)).thenReturn(0.8);

        var tracker = new FamiliarityTracker();
        var observer = new SocialObserver(tracker);
        observer.setGatingArbiter(gating);
        assertFalse(observer.shouldDirectConsolidate("test"));
    }

    // ── HormonalSystem getCandidateCategories ──

    @Test
    @DisplayName("getCandidateCategories returns flee when stress high")
    void candidateCategoriesStressHigh() {
        var h = new HormonalSystem(0.8, 0.2, 0.3);
        var cats = h.getCandidateCategories();
        assertTrue(cats.contains("flee"));
        assertTrue(cats.contains("survival"));
    }

    @Test
    @DisplayName("getCandidateCategories returns attack when aggression high")
    void candidateCategoriesAggressionHigh() {
        var h = new HormonalSystem(0.1, 0.8, 0.3);
        var cats = h.getCandidateCategories();
        assertTrue(cats.contains("attack"));
    }

    @Test
    @DisplayName("getCandidateCategories returns explore when curiosity high")
    void candidateCategoriesCuriosityHigh() {
        var h = new HormonalSystem(0.1, 0.2, 0.7);
        var cats = h.getCandidateCategories();
        assertTrue(cats.contains("explore"));
    }

    @Test
    @DisplayName("getCandidateCategories returns routine when stress and aggression low")
    void candidateCategoriesRoutine() {
        var h = new HormonalSystem(0.1, 0.2, 0.3);
        var cats = h.getCandidateCategories();
        assertTrue(cats.contains("routine"));
    }

    @Test
    @DisplayName("getCandidateCategories never returns empty")
    void candidateCategoriesNeverEmpty() {
        for (int i = 0; i < 20; i++) {
            var h = new HormonalSystem(Math.random(), Math.random(), Math.random());
            assertFalse(h.getCandidateCategories().isEmpty());
        }
    }
}
