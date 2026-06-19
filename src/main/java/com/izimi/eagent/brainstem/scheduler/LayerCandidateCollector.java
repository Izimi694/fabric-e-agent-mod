package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.SocialObserver;
import com.izimi.eagent.brainstem.innate.InnateReflex;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.brainstem.perception.PerceptionSnapshot;
import com.izimi.eagent.brainstem.perception.SalienceMap;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class LayerCandidateCollector {

    private static final float L0_SALIENCE = 0.9f;
    private static final float L1_SALIENCE = 0.8f;
    private static final float L2_SALIENCE = 0.6f;
    private static final float L3_SALIENCE = 0.4f;
    private static final float L4_SALIENCE = 0.3f;

    public List<SalienceMap.Candidate> collect(InnateReflexRegistry l0, OneShotAlarmSystem l1,
                                                ConditionedReflex l2, SocialObserver l3,
                                                PerceptionSnapshot snapshot,
                                                ServerPlayerEntity bot) {
        List<SalienceMap.Candidate> candidates = new ArrayList<>();

        if (l0 != null && bot != null) {
            collectL0(l0, bot, snapshot, candidates);
        }
        if (l1 != null && bot != null) {
            collectL1(l1, bot, snapshot, candidates);
        }
        if (l2 != null && bot != null) {
            collectL2(l2, bot, snapshot, candidates);
        }
        if (l3 != null && bot != null) {
            collectL3(l3, bot, snapshot, candidates);
        }
        if (snapshot != null) {
            collectL4(snapshot, candidates);
        }

        return candidates;
    }

    private void collectL0(InnateReflexRegistry l0, ServerPlayerEntity bot,
                            PerceptionSnapshot snapshot, List<SalienceMap.Candidate> candidates) {
        InnateReflex critical = l0.highest(bot, 0);
        if (critical != null && critical.critical()) {
            candidates.add(new SalienceMap.Candidate("survival_escape", L0_SALIENCE, "L0"));
        }
        InnateReflex highest = l0.highest(bot);
        if (highest != null) {
            float salience = L0_SALIENCE * highest.priority() / 10.0f;
            candidates.add(new SalienceMap.Candidate(highest.id(), salience, "L0"));
        }
    }

    private void collectL1(OneShotAlarmSystem l1, ServerPlayerEntity bot,
                            PerceptionSnapshot snapshot, List<SalienceMap.Candidate> candidates) {
        if (l1.hasThreatMatchNearby(bot)) {
            candidates.add(new SalienceMap.Candidate("flee", L1_SALIENCE, "L1"));
        }
    }

    private void collectL2(ConditionedReflex l2, ServerPlayerEntity bot,
                            PerceptionSnapshot snapshot, List<SalienceMap.Candidate> candidates) {
        double highestProf = l2.getHighestProficiency();
        if (highestProf >= 0.6) {
            candidates.add(new SalienceMap.Candidate("routine", (float) (L2_SALIENCE * highestProf), "L2"));
        }
    }

    private void collectL3(SocialObserver l3, ServerPlayerEntity bot,
                            PerceptionSnapshot snapshot, List<SalienceMap.Candidate> candidates) {
        int nearbyPlayers = l3.getNearbyPlayerCount();
        if (nearbyPlayers > 0) {
            float salience = Math.min(L3_SALIENCE, (float) (L3_SALIENCE * (nearbyPlayers / 4.0)));
            candidates.add(new SalienceMap.Candidate("social_interact", salience, "L3"));
        }
    }

    private void collectL4(PerceptionSnapshot snapshot, List<SalienceMap.Candidate> candidates) {
        var dv = snapshot.dense();
        if (dv.health() < 0.3) {
            candidates.add(new SalienceMap.Candidate("heal", 0.9f, "L4"));
        }
        if (dv.mobCount() > 3) {
            candidates.add(new SalienceMap.Candidate("combat", 0.7f, "L4"));
        }
        if (dv.incomingProjectiles() > 0) {
            candidates.add(new SalienceMap.Candidate("evade", 1.0f, "L4"));
        }
    }
}
