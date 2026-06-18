package com.izimi.eagent.brainstem.equipment;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SurvivalEquipmentManager {

    private static final Map<String, Integer> MATERIAL_TIER = Map.of(
            "wooden", 1, "stone", 2, "golden", 2, "iron", 3, "diamond", 4, "netherite", 5
    );

    private SurvivalEquipmentManager() {}

    public static void equipBestWeapon(ServerPlayerEntity bot) {
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            int score = getWeaponScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) return;
        selectSlot(bot, bestSlot);
    }

    public static void equipBestArmor(ServerPlayerEntity bot) {
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            if (bot.getEquippedStack(slot).isEmpty()) {
                equipArmorSlot(bot, slot);
            }
        }
    }

    public static void equipTotem(ServerPlayerEntity bot) {
        ItemStack offhand = bot.getOffHandStack();
        if (!offhand.isEmpty() && offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                bot.getInventory().offHand.set(0, stack.copy());
                bot.getInventory().removeStack(i);
                return;
            }
        }
    }

    public static boolean hasBowInHotbar(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasArrows(ServerPlayerEntity bot) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.ARROW)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasTotemInInventory(ServerPlayerEntity bot) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    private static int getWeaponScore(ItemStack stack) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase();
        if (!path.contains("sword") && !path.contains("axe")
                && !path.contains("trident") && !path.contains("mace")) return -1;

        int tier = 0;
        for (var entry : MATERIAL_TIER.entrySet()) {
            if (path.contains(entry.getKey())) {
                tier = entry.getValue();
                break;
            }
        }

        int typeBonus = 0;
        if (path.contains("sword")) typeBonus = 2;
        else if (path.contains("trident")) typeBonus = 2;
        else if (path.contains("mace")) typeBonus = 3;
        else if (path.contains("axe")) typeBonus = 1;

        return tier * 10 + typeBonus;
    }

    private static void equipArmorSlot(ServerPlayerEntity bot, EquipmentSlot slot) {
        int bestSlot = -1;
        int bestTier = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!isArmorForSlot(stack, slot)) continue;
            int tier = getArmorTier(stack);
            if (tier > bestTier) {
                bestTier = tier;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) return;
        ItemStack stack = bot.getInventory().getStack(bestSlot).copy();
        bot.getInventory().armor.set(slot.getEntitySlotId(), stack);
        bot.getInventory().removeStack(bestSlot);
    }

    private static boolean isArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase();
        return switch (slot) {
            case HEAD -> path.contains("helmet");
            case CHEST -> path.contains("chestplate");
            case LEGS -> path.contains("leggings");
            case FEET -> path.contains("boots");
            default -> false;
        };
    }

    private static int getArmorTier(ItemStack stack) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase();
        for (var entry : MATERIAL_TIER.entrySet()) {
            if (path.contains(entry.getKey())) return entry.getValue();
        }
        return 0;
    }

    private static void selectSlot(ServerPlayerEntity bot, int slot) {
        if (slot < 9) {
            bot.getInventory().selectedSlot = slot;
        } else {
            for (int i = 0; i < 9; i++) {
                if (bot.getInventory().getStack(i).isEmpty()) {
                    bot.getInventory().selectedSlot = i;
                    bot.getInventory().setStack(i, bot.getInventory().getStack(slot).copy());
                    bot.getInventory().removeStack(slot);
                    return;
                }
            }
        }
    }
}
