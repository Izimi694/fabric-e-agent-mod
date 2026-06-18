package com.izimi.eagent.cortex.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest {

    @Test @DisplayName("dig 任务分解为 move + dig")
    void digTaskPrependsMove() throws Exception {
        Method m = TaskManager.class.getDeclaredMethod("needsMovement", String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(null, "dig"));
        assertTrue((Boolean) m.invoke(null, "attack"));
        assertTrue((Boolean) m.invoke(null, "collect"));
        assertTrue((Boolean) m.invoke(null, "placeBlock"));
        assertFalse((Boolean) m.invoke(null, "move"));
        assertFalse((Boolean) m.invoke(null, "craft"));
        assertFalse((Boolean) m.invoke(null, ""));
        assertFalse((Boolean) m.invoke(null, (Object) null));
    }

    @Test @DisplayName("Task.extractAction 正确识别动作")
    void extractAction() {
        assertEquals("dig", Task.extractAction("挖树"));
        assertEquals("dig", Task.extractAction("砍5棵橡木"));
        assertEquals("attack", Task.extractAction("打僵尸"));
        assertEquals("craft", Task.extractAction("合成工作台"));
        assertEquals("move", Task.extractAction("去村庄"));
        assertEquals("move", Task.extractAction("探索"));
    }

    @Test @DisplayName("Task.extractTarget 提取目标（去除动作词后的剩余部分）")
    void extractTarget() {
        assertEquals("树", Task.extractTarget("挖树"));
        assertEquals("橡木", Task.extractTarget("挖5个橡木"));
        assertEquals("僵尸", Task.extractTarget("打僵尸"));
        assertEquals("工台", Task.extractTarget("合成工作台"));
        assertEquals("村庄", Task.extractTarget("去村庄"));
    }

    @Test @DisplayName("Task.extractCount 默认1")
    void extractCountDefault() {
        assertEquals(1, Task.extractCount("挖树"));
        assertEquals(1, Task.extractCount("打僵尸"));
        assertEquals(5, Task.extractCount("挖5个橡木"));
        assertEquals(10, Task.extractCount("砍10棵橡木"));
    }
}
