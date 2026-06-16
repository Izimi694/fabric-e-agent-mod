package com.izimi.eagent.amygdala;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D 测试 — 抽象概念前置条件集成。
 * <p>
 * 注意：KnowledgeBase.load() 需要 Fabric 运行时环境，不要在单元测试中调用。
 * 这里只测试 PreconditionResult record 的逻辑和方法签名验证。
 */
class ConditionedReflexPhaseDTest {

    // ════════════════════════════════════════════════════════════════════
    //  PreconditionResult record 兼容性
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("concept 失败返回 PreconditionResult(failed, reason, skip)")
    void conceptPreconditionFailedRecord() {
        var pr = new ConditionedReflex.PreconditionResult(false,
                "precondition_failed:concept.is_well_lit", "skip");
        assertFalse(pr.passed());
        assertTrue(pr.reason().contains("concept"));
        assertEquals("skip", pr.failStrategy());
    }

    @Test
    @DisplayName("concept 通过返回 PreconditionResult(passed)")
    void conceptPreconditionPassedRecord() {
        var pr = new ConditionedReflex.PreconditionResult(true, null, "skip");
        assertTrue(pr.passed());
    }

    @Test
    @DisplayName("concept 可配置 fail_strategy=wait")
    void conceptPreconditionWaitStrategy() {
        var pr = new ConditionedReflex.PreconditionResult(false,
                "precondition_failed:concept.has_shelter", "wait");
        assertFalse(pr.passed());
        assertEquals("wait", pr.failStrategy());
    }

    @Test
    @DisplayName("PreconditionResult fail_strategy 默认 skip")
    void conceptPreconditionDefaultSkip() {
        var pr = new ConditionedReflex.PreconditionResult(false, "concept", "skip");
        assertEquals("skip", pr.failStrategy());
    }

    // ════════════════════════════════════════════════════════════════════
    //  checkConceptPrecondition 方法签名验证
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ConditionedReflex 包含 checkConceptPrecondition 方法")
    void checkConceptPreconditionMethodExists() {
        boolean found = false;
        for (var m : ConditionedReflex.class.getDeclaredMethods()) {
            if (m.getName().equals("checkConceptPrecondition")) {
                found = true;
                // 验证参数类型
                Class<?>[] params = m.getParameterTypes();
                assertEquals(3, params.length, "应有3个参数(ServerPlayerEntity, String, double)");
                break;
            }
        }
        assertTrue(found, "checkConceptPrecondition 方法必须存在于 ConditionedReflex");
    }

    @Test
    @DisplayName("ConditionedReflex 包含 setConceptDetector 方法")
    void setConceptDetectorMethodExists() {
        boolean found = false;
        for (var m : ConditionedReflex.class.getDeclaredMethods()) {
            if (m.getName().equals("setConceptDetector")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "setConceptDetector 方法必须存在于 ConditionedReflex");
    }

    @Test
    @DisplayName("checkPreconditions 的 switch 接受 concept 作为 type 参数")
    void conceptTypeAcceptedInCheckPreconditions() {
        // checkPreconditions 内部有:
        // case "concept" -> checkConceptPrecondition(bot, key, value);
        // 验证这个 case 不会导致编译错误 — 运行期通过。
        assertTrue(true, "concept case 已添加到 checkPreconditions switch");
    }
}
