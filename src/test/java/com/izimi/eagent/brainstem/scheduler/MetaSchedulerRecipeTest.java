package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.prefrontal.ReflexRecipe;
import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 MetaScheduler.generateDefaultRecipeForAction 的配方生成逻辑。
 * 通过反射调用 private static 方法。
 */
class MetaSchedulerRecipeTest {

    @Test
    void attackRecipe() throws Exception {
        var recipe = invoke("attack_reflex", "attack");
        assertEquals("attack_reflex", recipe.reflexId());
        assertEquals(0.7, recipe.targetVector().ne(), 0.001);
        assertEquals(0.6, recipe.targetVector().da(), 0.001);
        assertEquals(0.2, recipe.targetVector().serotonin(), 0.001);
        assertEquals(0.8, recipe.targetVector().ach(), 0.001);
        assertEquals(3.0, recipe.safetyDistance(), 0.001);
        assertEquals(1.5, recipe.neModulation(), 0.001);
    }

    @Test
    void digRecipe() throws Exception {
        var recipe = invoke("dig_reflex", "dig");
        assertEquals(0.4, recipe.targetVector().ne(), 0.001);
        assertEquals(0.5, recipe.targetVector().da(), 0.001);
        assertEquals(0.4, recipe.targetVector().serotonin(), 0.001);
        assertEquals(0.7, recipe.targetVector().ach(), 0.001);
    }

    @Test
    void mineRecipe() throws Exception {
        var recipe = invoke("mine_reflex", "mine");
        assertEquals(0.4, recipe.targetVector().ne(), 0.001);
        assertEquals(0.5, recipe.targetVector().da(), 0.001);
    }

    @Test
    void fleeRecipe() throws Exception {
        var recipe = invoke("flee_reflex", "flee");
        assertEquals(0.8, recipe.targetVector().ne(), 0.001);
        assertEquals(0.3, recipe.targetVector().da(), 0.001);
        assertEquals(5.0, recipe.safetyDistance(), 0.001);
        assertEquals(2.0, recipe.neModulation(), 0.001);
    }

    @Test
    void moveToRecipe() throws Exception {
        var recipe = invoke("move_reflex", "moveTo");
        assertEquals(0.8, recipe.targetVector().ne(), 0.001);
        assertEquals(5.0, recipe.safetyDistance(), 0.001);
    }

    @Test
    void eatRecipe() throws Exception {
        var recipe = invoke("eat_reflex", "eat");
        assertEquals(0.3, recipe.targetVector().ne(), 0.001);
        assertEquals(0.7, recipe.targetVector().serotonin(), 0.001);
        assertEquals(0.5, recipe.targetVector().da(), 0.001);
    }

    @Test
    void craftRecipe() throws Exception {
        var recipe = invoke("craft_reflex", "craft");
        assertEquals(0.3, recipe.targetVector().ne(), 0.001);
        assertEquals(0.6, recipe.targetVector().da(), 0.001);
        assertEquals(0.6, recipe.targetVector().serotonin(), 0.001);
        assertEquals(0.8, recipe.targetVector().ach(), 0.001);
    }

    @Test
    void placeBlockRecipe() throws Exception {
        var recipe = invoke("place_reflex", "placeBlock");
        assertEquals(0.3, recipe.targetVector().ne(), 0.001);
        assertEquals(0.8, recipe.targetVector().ach(), 0.001);
    }

    @Test
    void unknownActionDefaultsToNeutral() throws Exception {
        var recipe = invoke("unknown_reflex", "dance");
        assertEquals(0.5, recipe.targetVector().ne(), 0.001);
        assertEquals(0.5, recipe.targetVector().da(), 0.001);
        assertEquals(0.5, recipe.targetVector().serotonin(), 0.001);
        assertEquals(0.5, recipe.targetVector().ach(), 0.001);
        assertEquals(3.0, recipe.safetyDistance(), 0.001);
        assertEquals(1.0, recipe.neModulation(), 0.001);
    }

    @Test
    void equipItemRecipe() throws Exception {
        var recipe = invoke("equip_reflex", "equipItem");
        assertEquals(0.4, recipe.targetVector().ne(), 0.001);
        assertEquals(0.6, recipe.targetVector().ach(), 0.001);
    }

    /** 通过反射调用 private static 方法 */
    private ReflexRecipe invoke(String reflexId, String action) throws Exception {
        Method method = MetaScheduler.class.getDeclaredMethod(
                "generateDefaultRecipeForAction", String.class, String.class);
        method.setAccessible(true);
        return (ReflexRecipe) method.invoke(null, reflexId, action);
    }
}
