package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.hippocampus.MemoryGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkingMemoryPool {

    public static final double DECAY = 0.7;
    public static final double MAX_INERTIA = 0.6;
    public static final int MAX_TRAJECTORY_POINTS = 1200;
    public static final int MIN_TRAJECTORY_FOR_COMPRESS = 100;
    public static final int COMPRESSION_HEARTBEAT_TICKS = 12000;
    public static final double SCHEMA_INERTIA_BONUS = 0.05;
    public static final int GRID_SIZE = 4;

    private final Map<String, Double> inertias = new HashMap<>();
    private String lastActionType = null;

    private final List<TrajectoryPoint> trajectoryBuffer = new ArrayList<>();
    private final List<Schema> activeSchemas = new ArrayList<>();
    private final Map<String, Schema> previousSchemas = new HashMap<>();
    private Double lastKnownX = null;
    private Double lastKnownZ = null;
    private int compressionTickCounter = 0;
    private MemoryGraph memoryGraph = null;

    private record TrajectoryPoint(double x, double z, float satisfaction, long tick) {}

    private record GridHeat(String key, float avgSatisfaction, double avgX, double avgZ, int count) {}

    public void tick() {
        for (var it = inertias.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            double decayed = entry.getValue() * DECAY;
            if (decayed < 0.001) {
                it.remove();
            } else {
                entry.setValue(decayed);
            }
        }

        compressionTickCounter++;
    }

    public void setInertia(String actionType, double weight) {
        if (actionType == null || actionType.isBlank()) return;
        lastActionType = actionType;
        inertias.put(actionType, Math.min(weight, MAX_INERTIA));
    }

    public double getInertia(String actionType) {
        if (actionType == null) return 0;
        Double val = inertias.get(actionType);
        double base = val != null ? Math.min(val, MAX_INERTIA) : 0;
        if (base > 0 && lastKnownX != null && lastKnownZ != null) {
            int cellX = Schema.gridFromCoord(lastKnownX);
            int cellZ = Schema.gridFromCoord(lastKnownZ);
            for (Schema s : activeSchemas) {
                if (s.gridX() == cellX && s.gridZ() == cellZ) {
                    return Math.min(base + SCHEMA_INERTIA_BONUS, MAX_INERTIA);
                }
            }
        }
        return base;
    }

    // ── Trajectory & Schema ──

    public void recordPosition(double x, double z, float satisfaction) {
        lastKnownX = x;
        lastKnownZ = z;
        trajectoryBuffer.add(new TrajectoryPoint(x, z, satisfaction, System.currentTimeMillis()));
        if (trajectoryBuffer.size() > MAX_TRAJECTORY_POINTS) {
            trajectoryBuffer.remove(0);
        }
        if (trajectoryBuffer.size() >= MAX_TRAJECTORY_POINTS) {
            compressTrajectoryToSchemas();
        }
    }

    public List<Schema> compressTrajectoryToSchemas() {
        if (trajectoryBuffer.size() < MIN_TRAJECTORY_FOR_COMPRESS) {
            return List.copyOf(activeSchemas);
        }

        Map<String, List<TrajectoryPoint>> gridCells = new HashMap<>();
        for (TrajectoryPoint p : trajectoryBuffer) {
            int gx = (int) Math.floor(p.x / GRID_SIZE);
            int gz = (int) Math.floor(p.z / GRID_SIZE);
            String key = Schema.gridKey(gx, gz);
            gridCells.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<GridHeat> heats = new ArrayList<>();
        for (var entry : gridCells.entrySet()) {
            List<TrajectoryPoint> points = entry.getValue();
            float totalSat = 0;
            double avgX = 0, avgZ = 0;
            for (TrajectoryPoint p : points) {
                totalSat += p.satisfaction;
                avgX += p.x;
                avgZ += p.z;
            }
            int count = points.size();
            avgX /= count;
            avgZ /= count;
            heats.add(new GridHeat(entry.getKey(), totalSat / count, avgX, avgZ, count));
        }

        heats.sort((a, b) -> Float.compare(b.avgSatisfaction, a.avgSatisfaction));
        List<GridHeat> top3 = heats.size() > 3 ? heats.subList(0, 3) : heats;
        var topKeys = top3.stream().map(GridHeat::key).collect(Collectors.toSet());

        List<Schema> newSchemas = new ArrayList<>();
        for (GridHeat gh : top3) {
            Schema prev = previousSchemas.get(gh.key);
            int hi = prev != null ? prev.consecutiveHiCount() + 1 : 1;
            int lo = 0;
            String[] parts = gh.key.split(":");
            int gx = Integer.parseInt(parts[1]);
            int gz = Integer.parseInt(parts[2]);
            Schema s = new Schema("schema_" + gh.key, "", gx, gz,
                gh.avgX, gh.avgZ, gh.avgSatisfaction, gh.count, hi, lo, hi >= 3);
            newSchemas.add(s);
        }

        for (var entry : previousSchemas.entrySet()) {
            if (topKeys.contains(entry.getKey())) continue;
            Schema prev = entry.getValue();
            Schema decayed = prev.withConsecutiveLo(prev.consecutiveLoCount() + 1);
            if (!decayed.canBeRemoved()) {
                newSchemas.add(decayed);
            }
        }

        activeSchemas.clear();
        activeSchemas.addAll(newSchemas);

        previousSchemas.clear();
        for (Schema s : newSchemas) {
            previousSchemas.put("g:" + s.gridX() + ":" + s.gridZ(), s);
        }

        trajectoryBuffer.clear();
        compressionTickCounter = 0;

        if (memoryGraph != null) {
            for (Schema s : activeSchemas) {
                memoryGraph.addSchema(s);
            }
        }

        return List.copyOf(activeSchemas);
    }

    public void heartbeatCompress() {
        if (compressionTickCounter >= COMPRESSION_HEARTBEAT_TICKS) {
            compressTrajectoryToSchemas();
        }
    }

    public Schema getSchemaForPosition(double x, double z) {
        int cellX = Schema.gridFromCoord(x);
        int cellZ = Schema.gridFromCoord(z);
        for (Schema s : activeSchemas) {
            if (s.gridX() == cellX && s.gridZ() == cellZ) return s;
        }
        return null;
    }

    public List<Schema> getActiveSchemas() {
        return Collections.unmodifiableList(activeSchemas);
    }

    public int getTrajectoryBufferSize() {
        return trajectoryBuffer.size();
    }

    public void setMemoryGraph(MemoryGraph mg) { this.memoryGraph = mg; }

    public void reset() {
        inertias.clear();
        lastActionType = null;
        trajectoryBuffer.clear();
        activeSchemas.clear();
        previousSchemas.clear();
        lastKnownX = null;
        lastKnownZ = null;
        compressionTickCounter = 0;
    }

    public Map<String, Double> getAllInertias() {
        return Collections.unmodifiableMap(inertias);
    }

    public String getLastActionType() { return lastActionType; }
}
