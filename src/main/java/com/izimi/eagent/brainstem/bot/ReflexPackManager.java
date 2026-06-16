package com.izimi.eagent.brainstem.bot;

import com.izimi.eagent.amygdala.ReflexConstants;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.cortex.api.HormonalPreset;
import com.izimi.eagent.cortex.api.PlaystylePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.hippocampus.MemoryGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ReflexPackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int PACK_VERSION = 2;

    private final UUID botId;
    private final BayesianModule bayesianModule;
    private BotInstance botInstance;
    private MemoryGraph memoryGraph;

    public void setBotInstance(BotInstance instance) {
        this.botInstance = instance;
    }

    public ReflexPackManager(UUID botId, BayesianModule bayesianModule) {
        this.botId = botId;
        this.bayesianModule = bayesianModule;
    }

    public void setMemoryGraph(MemoryGraph memoryGraph) {
        this.memoryGraph = memoryGraph;
    }

    public boolean exportPack(String packName, boolean includePrior) {
        Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
        Path conditionedDir = FileUtil.getBotConditionedDir(botId);

        if (!Files.exists(conditionedDir)) {
            LOGGER.warn("[ReflexPack] 没有反射可以导出: bot={}", botId);
            return false;
        }

        try {
            Map<String, Object> pack = buildPack(conditionedDir, includePrior);
            JsonUtil.writeToFileSafeAtomic(packFile, pack);
            Object refs = pack.get("reflexes");
            int count = refs instanceof Map ? ((Map<?, ?>) refs).size() : 0;
            LOGGER.info("[ReflexPack] 导出成功: {} ({} 反射, includePrior={})",
                    packName, count, includePrior);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ReflexPack] 导出失败: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildPack(Path conditionedDir, boolean includePrior) throws IOException {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("version", PACK_VERSION);
        pack.put("source", "E-Agent");
        pack.put("source_bot", botId.toString());
        pack.put("exported_at", Instant.now().toString());
        pack.put("include_prior", includePrior);

        Map<String, Object> reflexes = new LinkedHashMap<>();
        try (var stream = Files.list(conditionedDir)) {
            for (Path reflexFile : stream.toList()) {
                if (!reflexFile.toString().endsWith(".json")) continue;
                Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexFile);
                if (data != null) {
                    String skillId = (String) data.getOrDefault("skillId",
                            reflexFile.getFileName().toString().replace(".json", ""));
                    reflexes.put(skillId, data);
                }
            }
        }
        pack.put("reflexes", reflexes);

        if (includePrior) {
            Map<String, Double> prior = bayesianModule.getSharedPrior();
            Map<String, Double> filteredPrior = new HashMap<>();
            for (String reflexId : reflexes.keySet()) {
                if (prior.containsKey(reflexId)) {
                    filteredPrior.put(reflexId, prior.get(reflexId));
                }
            }
            pack.put("bayesian_prior", filteredPrior);
        }

        if (memoryGraph != null) {
            pack.put("memory_graph", memoryGraph.exportSkeleton());
        }

        if (botInstance != null) {
            var params = botInstance.getBotParams();
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("alpha", params.getAlpha());
            profile.put("beta", params.getBeta());
            profile.put("temperature", params.getTemperature());
            var h = botInstance.getHormonalSystem();
            Map<String, Object> hp = new LinkedHashMap<>();
            hp.put("stress", h.getStress());
            hp.put("aggression", h.getAggression());
            hp.put("curiosity", h.getCuriosity());
            hp.put("ne", h.getNE());
            hp.put("da", h.getDA());
            hp.put("serotonin", h.getSerotonin());
            hp.put("ach", h.getACh());
            profile.put("hormonal_preset", hp);
            var w = botInstance.getMotivationEngine().getPerspectiveWeights();
            if (w != null) profile.put("perspective_weights", w);
            pack.put("profile", profile);
        }

        return pack;
    }

    /** Parse a V2 profile section from the pack into a PlaystylePack.PackProfile record */
    @SuppressWarnings("unchecked")
    public static PlaystylePack.PackProfile parseProfile(Map<String, Object> profileData) {
        if (profileData == null) return null;
        double alpha = ((Number) profileData.getOrDefault("alpha", 0.0)).doubleValue();
        double beta = ((Number) profileData.getOrDefault("beta", 0.0)).doubleValue();
        double temp = ((Number) profileData.getOrDefault("temperature", 0.4)).doubleValue();
        Map<String, Object> hp = (Map<String, Object>) profileData.get("hormonal_preset");
        HormonalPreset preset = HormonalPreset.DEFAULT;
        if (hp != null) {
            preset = new HormonalPreset(
                ((Number) hp.getOrDefault("stress", 0.1)).doubleValue(),
                ((Number) hp.getOrDefault("aggression", 0.2)).doubleValue(),
                ((Number) hp.getOrDefault("curiosity", 0.3)).doubleValue(),
                ((Number) hp.getOrDefault("ne", 0.1)).doubleValue(),
                ((Number) hp.getOrDefault("da", 0.2)).doubleValue(),
                ((Number) hp.getOrDefault("serotonin", 0.3)).doubleValue(),
                ((Number) hp.getOrDefault("ach", 0.3)).doubleValue()
            );
        }
        Map<String, Double> weights = null;
        if (profileData.containsKey("perspective_weights")) {
            Map<String, Object> raw = (Map<String, Object>) profileData.get("perspective_weights");
            weights = new HashMap<>();
            for (var e : raw.entrySet()) {
                weights.put(e.getKey(), ((Number) e.getValue()).doubleValue());
            }
        }
        return new PlaystylePack.PackProfile(alpha, beta, temp, preset, weights != null ? weights : Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    public boolean importPack(String packName, boolean reset) {
        Path packFile = resolvePackFile(packName);
        Path conditionedDir = FileUtil.getBotConditionedDir(botId);

        Map<String, Object> pack = JsonUtil.readMapFromFileSafe(packFile);
        if (pack == null) {
            LOGGER.warn("[ReflexPack] 包不存在: {}", packName);
            return false;
        }

        Object rawReflexes = pack.get("reflexes");
        if (!(rawReflexes instanceof Map)) {
            LOGGER.warn("[ReflexPack] 包内没有反射: {}", packName);
            return false;
        }
        Map<?, ?> incomingReflexes = (Map<?, ?>) rawReflexes;

        try {
            Files.createDirectories(conditionedDir);
            int imported = 0;
            int skipped = 0;

            for (var entry : incomingReflexes.entrySet()) {
                String reflexId = (String) entry.getKey();
                Object rawData = entry.getValue();
                if (!(rawData instanceof Map)) {
                    skipped++;
                    continue;
                }
                Map<String, Object> incomingData = (Map<String, Object>) rawData;

                Path reflexFile = conditionedDir.resolve(reflexId + ".json");

                if (!reset && Files.exists(reflexFile)) {
                    Map<String, Object> existingData = JsonUtil.readMapFromFileSafe(reflexFile);
                    if (existingData != null) {
                        long existingTime = getLastUpdated(existingData);
                        long incomingTime = getLastUpdated(incomingData);
                        if (existingTime >= incomingTime) {
                            skipped++;
                            continue;
                        }
                    }
                }

                incomingData.put(ReflexConstants.KEY_LAST_ACCESSED, System.currentTimeMillis());
                JsonUtil.writeToFileSafeAtomic(reflexFile, incomingData);
                imported++;
            }

            applyPriorAndGraph(pack);

            LOGGER.info("[ReflexPack] 导入完成: {} ({} 个, 跳过 {} 个, reset={})",
                    packName, imported, skipped, reset);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ReflexPack] 导入失败: {}", e.getMessage());
            return false;
        }
    }

    private Path resolvePackFile(String packName) {
        Path packsDir = FileUtil.getReflexPacksDir();
        Path packFile = packsDir.resolve(packName + ".json");
        if (Files.exists(packFile)) return packFile;
        try (var stream = Files.walk(packsDir, 1)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.getFileName().toString().replace(".json", "").equals(packName))
                    .findFirst().orElse(packFile);
        } catch (IOException e) {
            LOGGER.warn("[ReflexPack] 扫描子目录失败: {}", e.getMessage());
            return packFile;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPriorAndGraph(Map<String, Object> pack) {
        if (pack.containsKey("bayesian_prior")) {
            Object rawPrior = pack.get("bayesian_prior");
            if (rawPrior instanceof Map) {
                for (var entry : ((Map<String, Object>) rawPrior).entrySet()) {
                    BayesianModule.setPrior(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
                BayesianModule.saveSharedPrior();
            }
        }
        if (pack.containsKey("memory_graph") && memoryGraph != null) {
            Object rawSkeleton = pack.get("memory_graph");
            if (rawSkeleton instanceof Map) {
                memoryGraph.importSkeleton((Map<String, Object>) rawSkeleton);
            }
        }
    }

    public static List<String> listPacks() {
        Path packsDir = FileUtil.getReflexPacksDir();
        if (!Files.exists(packsDir)) return Collections.emptyList();

        try (var stream = Files.walk(packsDir, 1)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> describePack(p))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static String describePack(Path path) {
        String name = path.getFileName().toString().replace(".json", "");
        Map<String, Object> meta = JsonUtil.readMapFromFileSafe(path);
        if (meta == null) return name + " (无法读取)";
        int ver = meta.containsKey("version") ? ((Number) meta.get("version")).intValue() : 1;
        Object refs = meta.get("reflexes");
        int count = refs instanceof Map ? ((Map<String, Object>) refs).size() : 0;
        boolean hasPrior = meta.containsKey("bayesian_prior")
                && meta.get("bayesian_prior") instanceof Map
                && !((Map<String, Object>) meta.get("bayesian_prior")).isEmpty();
        boolean hasProfile = meta.containsKey("profile");
        String extra = hasProfile ? ", 含参数配置" : "";
        return name + " (v" + ver + ", " + count + " 反射" + (hasPrior ? ", 含先验" : "") + extra + ")";
    }

    public static boolean deletePack(String packName) {
        Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
        if (!Files.exists(packFile)) return false;
        boolean deleted = FileUtil.deleteIfExists(packFile);
        if (deleted) {
            LOGGER.info("[ReflexPack] 删除成功: {}", packName);
        }
        return deleted;
    }

    private static long getLastUpdated(Map<String, Object> data) {
        Object val = data.get("lastUpdated");
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { }
        }
        Object solidified = data.get("solidifiedAt");
        if (solidified instanceof Number) return ((Number) solidified).longValue();
        return 0L;
    }
}
