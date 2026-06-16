package com.izimi.eagent.amygdala;

import com.izimi.eagent.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FailureReasonTest {

    private static final String REFLEX_ID = "reflex_dig_stone";
    private UUID botId;
    private Path tempDir;

    @TempDir
    Path sharedTempDir;

    @BeforeEach
    void setUp() {
        botId = UUID.randomUUID();
        tempDir = sharedTempDir.resolve(botId.toString());
        tempDir.toFile().mkdirs();
        ReflexIO.setOverrideDir(tempDir);
    }

    private void createReflex() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reflexId", REFLEX_ID);
        data.put("status", "healthy");
        data.put(ReflexConstants.KEY_ATOMS, List.of());
        JsonUtil.writeToFileSafeAtomic(tempDir.resolve(REFLEX_ID + ".json"), data);
    }

    @Test
    @DisplayName("appendFailureReason writes reason to reflex JSON")
    void appendFailureReason() {
        createReflex();
        Map<String, Object> ctx = Map.of("severity", "RETRY", "posterior", 0.15);
        ReflexIO.appendFailureReason(REFLEX_ID, botId, "RETRY_POSTERIOR_0.15", ctx);

        List<Map<String, Object>> reasons = ReflexIO.getFailureReasons(REFLEX_ID, botId, 10);
        assertEquals(1, reasons.size());
        assertEquals("RETRY_POSTERIOR_0.15", reasons.get(0).get("reason"));
        assertNotNull(reasons.get(0).get("timestamp"));
    }

    @Test
    @DisplayName("appendFailureReason maintains bounded list of 10 entries")
    void boundedList() {
        createReflex();
        for (int i = 0; i < 15; i++) {
            ReflexIO.appendFailureReason(REFLEX_ID, botId, "reason_" + i, Map.of("count", i));
        }
        List<Map<String, Object>> reasons = ReflexIO.getFailureReasons(REFLEX_ID, botId, 20);
        assertEquals(ReflexConstants.MAX_FAILURE_REASONS, reasons.size());
        assertEquals("reason_5", reasons.get(0).get("reason"));
        assertEquals("reason_14", reasons.get(reasons.size() - 1).get("reason"));
    }

    @Test
    @DisplayName("getFailureReasons returns empty for unknown reflex")
    void unknownReflex() {
        assertTrue(ReflexIO.getFailureReasons("nonexistent", botId, 5).isEmpty());
    }

    @Test
    @DisplayName("getFailureReasons respects maxCount")
    void respectsMaxCount() {
        createReflex();
        for (int i = 0; i < 5; i++) {
            ReflexIO.appendFailureReason(REFLEX_ID, botId, "reason_" + i, Map.of());
        }
        List<Map<String, Object>> reasons = ReflexIO.getFailureReasons(REFLEX_ID, botId, 3);
        assertEquals(3, reasons.size());
        assertEquals("reason_2", reasons.get(0).get("reason"));
    }

    @Test
    @DisplayName("appendFailureReason with null context stores empty map")
    void nullContext() {
        createReflex();
        ReflexIO.appendFailureReason(REFLEX_ID, botId, "DORMANT", null);
        List<Map<String, Object>> reasons = ReflexIO.getFailureReasons(REFLEX_ID, botId, 5);
        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).get("context") instanceof Map);
    }

    @Test
    @DisplayName("failureReasons field absent returns empty list")
    void absentField() {
        createReflex();
        assertTrue(ReflexIO.getFailureReasons(REFLEX_ID, botId, 5).isEmpty());
    }
}
