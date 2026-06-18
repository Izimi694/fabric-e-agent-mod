package com.izimi.eagent.brainstem.reflex;

import com.izimi.eagent.util.TagResolver;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public final class ReflexSimilarityScorer {

    private ReflexSimilarityScorer() {}

    public record FeatureVector(double[] features) {
        public double cosineSimilarity(FeatureVector other) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < features.length; i++) {
                dot += features[i] * other.features[i];
                normA += features[i] * features[i];
                normB += other.features[i] * other.features[i];
            }
            double denom = Math.sqrt(normA) * Math.sqrt(normB);
            return denom < 1e-10 ? 0 : dot / denom;
        }

        public boolean matches(FeatureVector other, double threshold) {
            return cosineSimilarity(other) >= threshold;
        }
    }

    private static final List<String> ACTION_TYPES = List.of("dig", "attack", "moveTo", "equipItem", "craft");
    private static final List<String> CATEGORIES = List.of("tree_log", "ore", "crop", "common_block", "hostile", "passive");
    private static final int FEATURE_DIMS = ACTION_TYPES.size() + CATEGORIES.size();

    public static FeatureVector fromReflex(Map<String, Object> reflexData) {
        double[] features = new double[FEATURE_DIMS];

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) reflexData.get("atoms");
        Set<String> actionSet = new HashSet<>();
        Set<String> catSet = new HashSet<>();

        if (atoms != null) {
            for (var atom : atoms) {
                String action = (String) atom.get("action");
                if (action != null) actionSet.add(action);
                String target = (String) atom.get("atomTarget");
                if (target == null) target = (String) atom.get("target");
                if (target != null) {
                    String cat = TagResolver.findCategory(target);
                    if (cat != null) catSet.add(cat);
                }
            }
        }

        String category = (String) reflexData.get("category");
        if (category != null) {
            String cat = TagResolver.findCategory(category);
            if (cat != null) catSet.add(cat);
        }

        for (int i = 0; i < ACTION_TYPES.size(); i++) {
            if (actionSet.contains(ACTION_TYPES.get(i))) features[i] = 1;
        }
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (catSet.contains(CATEGORIES.get(i))) features[ACTION_TYPES.size() + i] = 1;
        }

        return new FeatureVector(features);
    }

    public static FeatureVector fromContext(ServerPlayerEntity bot) {
        double[] features = new double[FEATURE_DIMS];
        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        Set<String> nearbyCats = new HashSet<>();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    String cat = TagResolver.findCategory(blockId);
                    if (cat != null) nearbyCats.add(cat);
                }
            }
        }

        var entities = world.getEntitiesByClass(LivingEntity.class,
                bot.getBoundingBox().expand(8), e -> e.isAlive() && e != bot);
        for (var entity : entities) {
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            String cat = TagResolver.findCategory(entityId);
            if (cat != null) nearbyCats.add(cat);
        }

        boolean hasDigTarget = nearbyCats.contains("tree_log") || nearbyCats.contains("ore")
                || nearbyCats.contains("crop") || nearbyCats.contains("common_block");
        if (hasDigTarget) features[0] = 1;
        boolean hasAttackTarget = nearbyCats.contains("hostile") || nearbyCats.contains("passive");
        if (hasAttackTarget) features[1] = 1;

        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (nearbyCats.contains(CATEGORIES.get(i))) {
                features[ACTION_TYPES.size() + i] = 1;
            }
        }

        return new FeatureVector(features);
    }

    public static double computeSimilarity(Map<String, Object> reflexData, ServerPlayerEntity bot) {
        return fromReflex(reflexData).cosineSimilarity(fromContext(bot));
    }
}
