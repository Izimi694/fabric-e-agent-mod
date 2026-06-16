package com.izimi.eagent.amygdala;

public final class ReflexConstants {

    private ReflexConstants() {}

    // ── Reflex status values ──
    public static final String STATUS_HEALTHY = "healthy";
    public static final String STATUS_TRIAL = "trial";
    public static final String STATUS_DORMANT = "dormant";
    public static final String STATUS_WATCHING = "watching";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_IMPOSSIBLE = "impossible";
    public static final String STATUS_DEPRECATED = "deprecated";

    // ── Reflex JSON field keys ──
    public static final String KEY_STATUS = "status";
    public static final String KEY_SHORT_TERM_WEIGHT = "shortTermWeight";
    public static final String KEY_LONG_TERM_BASELINE = "longTermBaseline";
    public static final String KEY_PROFICIENCY = "proficiency";
    public static final String KEY_SKILL_ID = "skill_id";
    public static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_ATOMS = "atoms";
    public static final String KEY_TRIGGER = "trigger";
    public static final String KEY_STEPS = "steps";
    public static final String KEY_ACTION = "action";
    public static final String KEY_TARGET = "target";
    public static final String KEY_SOURCE = "source";
    public static final String KEY_VERSION = "version";
    public static final String KEY_EXECUTION_COUNT = "executionCount";
    public static final String KEY_SUCCESS_RATE = "successRate";
    public static final String KEY_TRIAL_SUCCESSES = "trialSuccesses";
    public static final String KEY_TRIAL_FAILURES = "trialFailures";
    public static final String KEY_LAST_ACCESSED = "lastAccessedAt";

    public static final String KEY_FAILURE_REASONS = "failureReasons";
    public static final String KEY_FAILURE_REASON = "reason";
    public static final String KEY_FAILURE_CONTEXT = "context";
    public static final String KEY_FAILURE_TIMESTAMP = "timestamp";
    public static final int MAX_FAILURE_REASONS = 10;

    // ── Decay ──
    public static final double DECAY_RATE_PER_HOUR = 0.0003;
    public static final double DECAY_MIN = 0.3;
}
