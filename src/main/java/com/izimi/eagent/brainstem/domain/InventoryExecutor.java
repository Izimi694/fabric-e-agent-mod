package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class InventoryExecutor implements DomainExecutor<InventoryCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final Set<String> HANDLED_TYPES = Set.of(
            "equipItem", "useItem", "dropItem", "openBlock", "closeWindow", "clickSlot");

    private FailureContext failureContext;

    @Override
    public boolean canHandle(String commandType) {
        return HANDLED_TYPES.contains(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(InventoryCommand command) {
        ActionResult result = switch (command.action()) {
            case "equipItem" -> equipItem(command.bot(), command.itemName());
            case "useItem" -> useItem(command.bot());
            case "dropItem" -> dropItem(command.bot(), command.slot());
            case "openBlock" -> openBlock(command.bot(), command.blockPos());
            case "closeWindow" -> closeWindow(command.bot());
            case "clickSlot" -> clickSlot(command.bot(), command.slot(), command.button());
            default -> {
                failureContext = FailureContext.of(command.action(), "未知 Inventory 动作");
                yield ActionResult.fail("未知 Inventory 动作: " + command.action());
            }
        };
        if (!result.success() && !result.executed()) {
            failureContext = FailureContext.of(command.action(), result.message());
        } else {
            failureContext = null;
        }
        return CompletableFuture.completedFuture(result);
    }

    private ActionResult equipItem(ServerPlayerEntity bot, String itemName) {
        if (bot == null || itemName == null) return ActionResult.unable("equipItem: 参数无效");

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                if (id.toLowerCase().contains(itemName.toLowerCase())) {
                    if (i < 9) {
                        bot.getInventory().selectedSlot = i;
                    } else {
                        bot.getInventory().selectedSlot = 0;
                    }
                    return ActionResult.success("装备: " + id);
                }
            }
        }
        return ActionResult.unable("背包中没有: " + itemName);
    }

    private ActionResult useItem(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("useItem: bot为null");
        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) return ActionResult.unable("主手没有物品");
        bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        bot.getInventory().markDirty();
        return ActionResult.success("使用物品");
    }

    private ActionResult dropItem(ServerPlayerEntity bot, int slot) {
        if (bot == null) return ActionResult.unable("dropItem: bot为null");
        if (slot < 0) {
            if (bot.getMainHandStack().isEmpty()) return ActionResult.unable("手持没有物品");
            bot.dropSelectedItem(true);
            return ActionResult.success("丢弃手持物品");
        }
        if (slot >= 36) return ActionResult.unable("无效槽位");
        var stack = bot.getInventory().getStack(slot);
        if (stack.isEmpty()) return ActionResult.unable("槽位为空");
        bot.getInventory().removeStack(slot);
        return ActionResult.success("丢弃: slot " + slot);
    }

    private ActionResult openBlock(ServerPlayerEntity bot, BlockPos pos) {
        if (bot == null || pos == null) return ActionResult.unable("openBlock: 参数无效");
        if (pos.getSquaredDistance(bot.getBlockPos()) > 25.0) {
            return ActionResult.partial(0.3, "距离太远");
        }
        bot.openHandledScreen(bot.getServerWorld().getBlockState(pos)
                .createScreenHandlerFactory(bot.getServerWorld(), pos));
        return ActionResult.success("打开: " + pos.toShortString());
    }

    private ActionResult closeWindow(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("closeWindow: bot为null");
        if (bot.currentScreenHandler != null && bot.currentScreenHandler != bot.playerScreenHandler) {
            bot.closeHandledScreen();
            return ActionResult.success("关闭窗口");
        }
        return ActionResult.success("无窗口可关闭");
    }

    private ActionResult clickSlot(ServerPlayerEntity bot, int slot, int button) {
        if (bot == null) return ActionResult.unable("clickSlot: bot为null");
        ScreenHandler handler = bot.currentScreenHandler;
        if (handler == null || handler == bot.playerScreenHandler) {
            return ActionResult.unable("没有打开的容器");
        }
        try {
            handler.onSlotClick(slot, button, SlotActionType.PICKUP, bot);
            return ActionResult.success("点击槽位: " + slot);
        } catch (Exception e) {
            return ActionResult.fail("点击失败: " + e.getMessage());
        }
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return failureContext; }
}
