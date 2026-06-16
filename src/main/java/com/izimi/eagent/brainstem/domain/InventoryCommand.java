package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record InventoryCommand(
        ServerPlayerEntity bot,
        String action,
        String itemName,
        int slot,
        int button,
        BlockPos blockPos,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return action; }

    public static InventoryCommand equipItem(ServerPlayerEntity bot, String itemName, String reason) {
        return new InventoryCommand(bot, "equipItem", itemName, 0, 0, null, reason);
    }

    public static InventoryCommand useItem(ServerPlayerEntity bot, String reason) {
        return new InventoryCommand(bot, "useItem", null, 0, 0, null, reason);
    }

    public static InventoryCommand dropItem(ServerPlayerEntity bot, int slot, String reason) {
        return new InventoryCommand(bot, "dropItem", null, slot, 0, null, reason);
    }

    public static InventoryCommand openBlock(ServerPlayerEntity bot, BlockPos pos, String reason) {
        return new InventoryCommand(bot, "openBlock", null, 0, 0, pos, reason);
    }

    public static InventoryCommand closeWindow(ServerPlayerEntity bot, String reason) {
        return new InventoryCommand(bot, "closeWindow", null, 0, 0, null, reason);
    }

    public static InventoryCommand clickSlot(ServerPlayerEntity bot, int slot, int button, String reason) {
        return new InventoryCommand(bot, "clickSlot", null, slot, button, null, reason);
    }
}
