package com.izimi.eagent.brainstem.domain;

public abstract class ExecutorCPG {
    protected int phaseTick;

    public void tickPhase() {
        phaseTick++;
    }

    public void resetPhase() {
        phaseTick = 0;
    }

    public int getPhaseTick() { return phaseTick; }
    public double getPhaseProgress() { return 0; }

    public static class MotionCPG extends ExecutorCPG {
        private static final int WALK_CYCLE = 20;
        private static final double JUMP_COOLDOWN = 0.5;
        private int jumpCooldownTicks;

        @Override
        public void tickPhase() {
            super.tickPhase();
            if (jumpCooldownTicks > 0) jumpCooldownTicks--;
        }

        public double getWalkPhase() {
            return (phaseTick % WALK_CYCLE) / (double) WALK_CYCLE;
        }

        public boolean canJump() { return jumpCooldownTicks <= 0; }
        public void onJump() { jumpCooldownTicks = (int)(JUMP_COOLDOWN * 20); }
        public boolean isSprintingRecommended(double urgency) { return urgency > 0.5 && getPhaseTick() % 3 == 0; }
    }

    public static class DigCPG extends ExecutorCPG {
        private static final int SWING_INTERVAL = 7;
        private int swingTicks;

        @Override
        public void tickPhase() {
            super.tickPhase();
            swingTicks++;
        }

        @Override
        public void resetPhase() {
            super.resetPhase();
            swingTicks = 0;
        }

        public boolean shouldSwing() { return swingTicks >= SWING_INTERVAL; }
        public void onSwing() { swingTicks = 0; }
        public double getBreakProgress(int breakingTicks, int breakTimeTicks) {
            if (breakTimeTicks <= 0) return 0;
            return Math.min(1.0, (double) breakingTicks / breakTimeTicks);
        }
    }

    public static class CombatCPG extends ExecutorCPG {
        private static final int ATTACK_COOLDOWN = 10;
        private int attackCooldownTicks;

        @Override
        public void tickPhase() {
            super.tickPhase();
            if (attackCooldownTicks > 0) attackCooldownTicks--;
        }

        public boolean canAttack() { return attackCooldownTicks <= 0; }
        public void onAttack() { attackCooldownTicks = ATTACK_COOLDOWN; }
        public int getCooldownTicks() { return attackCooldownTicks; }
    }
}
