package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CraftExecutor implements DomainExecutor<CraftCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    // CraftingScreenHandler: 46 slots total
    private static final int CT_RESULT = 0;
    private static final int CT_GRID_START = 1;
    private static final int CT_GRID_END = 9;
    private static final int CT_GRID_WIDTH = 3;
    private static final int CT_INV_START = 10;
    private static final int CT_HOTBAR_START = 37;

    // PlayerScreenHandler (2x2): 41 slots total
    private static final int INV_RESULT = 0;
    private static final int INV_GRID_START = 1;
    private static final int INV_GRID_END = 4;
    private static final int INV_GRID_WIDTH = 2;
    private static final int INV_INV_START = 5;
    private static final int INV_HOTBAR_START = 32;

    private FailureContext failureContext;

    @Override
    public boolean canHandle(String commandType) {
        return "craft".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(CraftCommand command) {
        ServerPlayerEntity bot = command.bot();
        String itemId = command.itemId();

        if (bot == null || itemId == null || itemId.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.unable("craft: 参数无效"));
        }

        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            failureContext = FailureContext.of("craft", "无效物品ID: " + itemId);
            return CompletableFuture.completedFuture(ActionResult.fail("无效物品ID: " + itemId));
        }

        CraftingRecipe recipe = findRecipe(bot.getServerWorld(), id);
        if (recipe == null) {
            failureContext = FailureContext.of("craft", "无此配方: " + itemId);
            return CompletableFuture.completedFuture(ActionResult.fail("无此配方: " + itemId));
        }

        ScreenHandler handler = openCraftingUI(bot, recipe);
        if (handler == null) {
            failureContext = FailureContext.of("craft", "无法打开合成界面");
            return CompletableFuture.completedFuture(ActionResult.fail("无法打开合成界面"));
        }

        try {
            placeIngredientsInGrid(bot, recipe, handler);
            ActionResult result = collectResult(bot, handler, itemId);
            failureContext = null;
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            cleanup(bot, e);
            failureContext = FailureContext.of("craft", "合成异常: " + e.getMessage());
            return CompletableFuture.completedFuture(ActionResult.fail("合成失败: " + e.getMessage()));
        }
    }

    private CraftingRecipe findRecipe(ServerWorld world, Identifier itemId) {
        var all = world.getRecipeManager().listAllOfType(RecipeType.CRAFTING);
        for (RecipeEntry<CraftingRecipe> entry : all) {
            ItemStack result = entry.value().getResult(world.getRegistryManager());
            if (Registries.ITEM.getId(result.getItem()).equals(itemId)) return entry.value();
        }
        return null;
    }

    private ScreenHandler openCraftingUI(ServerPlayerEntity bot, CraftingRecipe recipe) {
        boolean needsTable = !recipe.fits(2, 2);
        int craftable = countCraftable(recipe, bot);
        if (craftable <= 0) return null;

        if (needsTable) {
            BlockPos tablePos = findBlockPosByName(bot.getServerWorld(), bot, "crafting_table");
            if (tablePos == null) return null;
            openBlock(bot, tablePos);
        } else {
            openInventory(bot);
        }
        return bot.currentScreenHandler;
    }

    private void placeIngredientsInGrid(ServerPlayerEntity bot, CraftingRecipe recipe, ScreenHandler handler) {
        boolean needsTable = handler instanceof CraftingScreenHandler;
        int gridWidth = needsTable ? CT_GRID_WIDTH : INV_GRID_WIDTH;
        int gridStart = needsTable ? CT_GRID_START : INV_GRID_START;
        int invStart = needsTable ? CT_INV_START : INV_INV_START;
        int hotbarStart = needsTable ? CT_HOTBAR_START : INV_HOTBAR_START;

        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            placeShaped(shaped, ingredients, bot, gridWidth, gridStart, invStart, hotbarStart);
        } else {
            placeShapeless(ingredients, bot, gridStart, invStart, hotbarStart, handler instanceof CraftingScreenHandler);
        }
    }

    private void placeShaped(ShapedRecipe shaped, List<Ingredient> ingredients, ServerPlayerEntity bot,
                             int gridWidth, int gridStart, int invStart, int hotbarStart) {
        int rw = shaped.getWidth();
        int rh = shaped.getHeight();
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int idx = row * rw + col;
                if (idx >= ingredients.size()) return;
                Ingredient ing = ingredients.get(idx);
                if (ing.isEmpty()) continue;
                int gridSlot = row * gridWidth + col + gridStart;
                moveIngredientToSlot(bot, ing, gridSlot, invStart, hotbarStart);
            }
        }
    }

    private void placeShapeless(List<Ingredient> ingredients, ServerPlayerEntity bot,
                                int gridStart, int invStart, int hotbarStart, boolean needsTable) {
        int slot = gridStart;
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            if (slot > (needsTable ? CT_GRID_END : INV_GRID_END)) return;
            moveIngredientToSlot(bot, ing, slot, invStart, hotbarStart);
            slot++;
        }
    }

    private void moveIngredientToSlot(ServerPlayerEntity bot, Ingredient ing, int targetSlot,
                                      int invStart, int hotbarStart) {
        ScreenHandler handler = bot.currentScreenHandler;
        if (handler == null) return;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty() || !ing.test(stack)) continue;

            int sourceSlot = i < 27 ? (i + invStart) : (i - 27 + hotbarStart);
            handler.onSlotClick(sourceSlot, 0, SlotActionType.PICKUP, bot);
            handler.onSlotClick(targetSlot, 0, SlotActionType.PICKUP, bot);
            return;
        }
    }

    private ActionResult collectResult(ServerPlayerEntity bot, ScreenHandler handler, String itemId) {
        boolean needsTable = handler instanceof CraftingScreenHandler;
        int resultSlot = needsTable ? CT_RESULT : INV_RESULT;
        ItemStack resultStack = handler.getSlot(resultSlot).getStack();
        if (resultStack.isEmpty()) {
            closeWindow(bot);
            return ActionResult.fail("合成失败: 材料摆放有误");
        }
        handler.onSlotClick(resultSlot, 0, SlotActionType.PICKUP, bot);
        int count = resultStack.getCount();
        closeWindow(bot);
        return ActionResult.success("合成 " + itemId + " x" + count + " 完成");
    }

    private void cleanup(ServerPlayerEntity bot, Exception e) {
        try { closeWindow(bot); } catch (Exception ignored) {}
    }

    private int countCraftable(CraftingRecipe recipe, ServerPlayerEntity bot) {
        int maxSets = Integer.MAX_VALUE;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            int matching = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = bot.getInventory().getStack(i);
                if (!stack.isEmpty() && ing.test(stack)) {
                    matching += stack.getCount();
                }
            }
            if (matching == 0) return 0;
            maxSets = Math.min(maxSets, matching);
        }
        return maxSets;
    }

    private void openBlock(ServerPlayerEntity bot, BlockPos pos) {
        if (pos.getSquaredDistance(bot.getBlockPos()) > 25.0) return;
        bot.openHandledScreen(bot.getServerWorld().getBlockState(pos).createScreenHandlerFactory(bot.getServerWorld(), pos));
    }

    private void openInventory(ServerPlayerEntity bot) {
        if (bot.currentScreenHandler == null) {
            bot.currentScreenHandler = bot.playerScreenHandler;
        }
    }

    private void closeWindow(ServerPlayerEntity bot) {
        if (bot.currentScreenHandler != null && bot.currentScreenHandler != bot.playerScreenHandler) {
            bot.closeHandledScreen();
        }
    }

    private BlockPos findBlockPosByName(ServerWorld world, ServerPlayerEntity bot, String name) {
        BlockPos bp = bot.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = bp.add(dx, dy, dz);
                    if (world.getBlockState(pos).isAir()) continue;
                    String id = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
                    if (id.toLowerCase().contains(name.toLowerCase())) return pos;
                }
            }
        }
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return failureContext; }
}
