package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.amygdala.learning.BehaviorEvent;
import com.izimi.aiplayermod.bayesian.BayesianModule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SocialObserver {

    private static final long WINDOW_MS = 60_000;
    private static final double CONTROLLABILITY_GATE = 0.3;

    private final Map<String, Deque<BehaviorEvent>> playerWindows = new ConcurrentHashMap<>();
    private final Set<String> nearbyPlayers = ConcurrentHashMap.newKeySet();
    private final FamiliarityTracker familiarityTracker;
    private BayesianModule bayesianModule;

    public SocialObserver(FamiliarityTracker familiarityTracker) {
        this.familiarityTracker = familiarityTracker;
    }

    public void setBayesianModule(BayesianModule bayesianModule) {
        this.bayesianModule = bayesianModule;
    }

    /**
     * 环境可控性门控: 低于阈值 → 观察到的行为可直接固化(环境不可控),
     * 否则需要贝叶斯验证后才能固化.
     */
    public boolean shouldDirectConsolidate(String reflexId) {
        if (bayesianModule == null) return false;
        double controllability = bayesianModule.computeControllability(reflexId, null);
        return controllability < CONTROLLABILITY_GATE;
    }

    public void onEvent(BehaviorEvent event) {
        if (event.playerName() == null || event.playerName().isEmpty()) return;

        playerWindows.computeIfAbsent(event.playerName(), k -> new ArrayDeque<>()).addLast(event);
        prunePlayerWindow(event.playerName());
        familiarityTracker.recordInteraction(event.playerName());
    }

    public void markNearbyPlayers(Collection<String> playerNames) {
        nearbyPlayers.clear();
        nearbyPlayers.addAll(playerNames);
        for (String name : playerNames) {
            familiarityTracker.recordPresence(name);
        }
    }

    public Map<String, Deque<BehaviorEvent>> getPlayerWindows() {
        return Collections.unmodifiableMap(playerWindows);
    }

    public int getObservedPlayerCount() {
        return (int) playerWindows.values().stream().filter(w -> !w.isEmpty()).count();
    }

    public int getNearbyPlayerCount() {
        return nearbyPlayers.size();
    }

    private void prunePlayerWindow(String playerName) {
        Deque<BehaviorEvent> window = playerWindows.get(playerName);
        if (window == null) return;
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!window.isEmpty() && window.peekFirst().timestamp() < cutoff) {
            window.pollFirst();
        }
        if (window.isEmpty()) {
            playerWindows.remove(playerName);
        }
    }
}
