package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.brainstem.perception.AffordanceRouter;
import com.izimi.eagent.brainstem.perception.AffordanceRouter.SortedCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ActionSorter {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public static final double LATERAL_INHIBITION_FACTOR = 0.1;
    public static final double DEFAULT_GAIN = 1.0;
    public static final double DEFAULT_TEMPERATURE = 0.5;
    public static final double SOFTMAX_THRESHOLD = 0.01;

    private final WorkingMemoryPool workingMemory;

    public ActionSorter(WorkingMemoryPool workingMemory) {
        this.workingMemory = workingMemory;
    }

    public BlendedAction select(List<SortedCandidate> sortedCandidates,
                                 double serotoninRatio, double dopamine, double pressure) {
        if (sortedCandidates == null || sortedCandidates.isEmpty()) {
            String lastAction = workingMemory.getLastActionType();
            if (lastAction != null) {
                double inertia = workingMemory.getInertia(lastAction);
                if (inertia > SOFTMAX_THRESHOLD) {
                    return new BlendedAction(lastAction, inertia, 0);
                }
            }
            return BlendedAction.NONE;
        }

        List<ExcitementCandidate> excited = new ArrayList<>();
        for (SortedCandidate sc : sortedCandidates) {
            double base = sc.adjustedSalience();
            double inertia = workingMemory.getInertia(sc.targetType());
            double excitement = base + inertia * 0.3;
            excited.add(new ExcitementCandidate(sc.targetType(), excitement));
        }

        double totalOthers = excited.stream().mapToDouble(e -> e.excitement).sum();
        for (ExcitementCandidate e : excited) {
            double inhibition = LATERAL_INHIBITION_FACTOR * (totalOthers - e.excitement);
            e.excitement = Math.max(0, e.excitement - inhibition);
        }

        double gain = computeGain(serotoninRatio);
        double temperature = clampTemperature(dopamine, pressure);

        double[] probabilities = softmax(excited, gain, temperature);

        int bestIdx = 0;
        double bestProb = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > bestProb) {
                bestProb = probabilities[i];
                bestIdx = i;
            }
        }

        ExcitementCandidate chosen = excited.get(bestIdx);
        double weight = probabilities[bestIdx];

        workingMemory.setInertia(chosen.targetType, weight);

        if (LOGGER.isInfoEnabled()) {
            var dist = new StringBuilder();
            for (int i = 0; i < excited.size(); i++) {
                if (i > 0) dist.append(", ");
                dist.append(excited.get(i).targetType).append("=").append(String.format("%.3f", probabilities[i]));
            }
            LOGGER.info("[AS] selected {} (p={}, gain={}, temp={}) dist=[{}]",
                chosen.targetType,
                String.format("%.3f", weight),
                String.format("%.2f", gain),
                String.format("%.2f", temperature),
                dist.toString());
        }
        return new BlendedAction(chosen.targetType, weight, 0);
    }

    static double computeGain(double serotoninRatio) {
        return 1.0 / (1.0 + Math.max(0, serotoninRatio));
    }

    static double clampTemperature(double dopamine, double pressure) {
        double raw = dopamine + pressure;
        return Math.max(0.05, Math.min(0.8, raw));
    }

    static double[] softmax(List<ExcitementCandidate> candidates, double gain, double temperature) {
        int n = candidates.size();
        double[] values = new double[n];

        if (temperature < 0.001) {
            double maxEx = candidates.get(0).excitement;
            int maxIdx = 0;
            for (int i = 1; i < n; i++) {
                if (candidates.get(i).excitement > maxEx) {
                    maxEx = candidates.get(i).excitement;
                    maxIdx = i;
                }
            }
            values[maxIdx] = 1.0;
            return values;
        }

        double maxInput = Double.NEGATIVE_INFINITY;
        for (ExcitementCandidate c : candidates) {
            if (c.excitement > maxInput) maxInput = c.excitement;
        }

        double sum = 0;
        for (int i = 0; i < n; i++) {
            double input = candidates.get(i).excitement * gain / temperature;
            values[i] = Math.exp(input - maxInput * gain / temperature);
            sum += values[i];
        }

        if (sum < 1e-12) {
            values[0] = 1.0;
            for (int i = 1; i < n; i++) values[i] = 0;
            return values;
        }

        for (int i = 0; i < n; i++) {
            values[i] /= sum;
        }
        return values;
    }

    static class ExcitementCandidate {
        final String targetType;
        double excitement;
        ExcitementCandidate(String targetType, double excitement) {
            this.targetType = targetType;
            this.excitement = excitement;
        }
    }
}
