package com.izimi.eagent.brainstem.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

public class ActionChain {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final List<IAction> actions;
    private int currentIndex = 0;
    private boolean completed = false;
    private boolean failed = false;

    public ActionChain(List<IAction> actions) {
        this.actions = actions;
    }

    public ActionState tick(ServerWorld world, ServerPlayerEntity bot) {
        if (completed) return ActionState.SUCCESS;
        if (failed) return ActionState.FAILED;
        if (currentIndex >= actions.size()) {
            completed = true;
            return ActionState.SUCCESS;
        }

        IAction current = actions.get(currentIndex);
        ActionState state = current.tick(world, bot);

        if (state == ActionState.SUCCESS) {
            LOGGER.info("[ActionChain] 步骤{}完成: {}", currentIndex, current.getClass().getSimpleName());
            currentIndex++;
            if (currentIndex >= actions.size()) {
                completed = true;
                return ActionState.SUCCESS;
            }
            return ActionState.RUNNING;
        }

        if (state == ActionState.FAILED) {
            LOGGER.warn("[ActionChain] 步骤{}失败: {}", currentIndex, current.getClass().getSimpleName());
            failed = true;
            return ActionState.FAILED;
        }

        return ActionState.RUNNING;
    }

    public boolean isDone() {
        return completed || failed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public void reset() {
        currentIndex = 0;
        completed = false;
        failed = false;
        for (IAction a : actions) {
            a.reset();
        }
    }

    public int getCurrentIndex() {
        return currentIndex;
    }
}
