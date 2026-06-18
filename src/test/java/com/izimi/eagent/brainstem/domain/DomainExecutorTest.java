package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DomainExecutorTest {

    /* ──────────────── PlaceExecutor ──────────────── */

    private final PlaceExecutor place = new PlaceExecutor();

    @Test @DisplayName("Place: canHandle placeBlock")
    void placeCanHandle() {
        assertTrue(place.canHandle("placeBlock"));
        assertFalse(place.canHandle("craft"));
        assertFalse(place.canHandle(null));
    }

    @Test @DisplayName("Place: null bot 返回 unable")
    void placeNullBot() {
        PlaceCommand cmd = new PlaceCommand(null, BlockPos.ORIGIN, "up", "test");
        ActionResult r = place.submit(cmd).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Place: 失败后 FailureContext 非空")
    void placeFailureContext() {
        place.submit(new PlaceCommand(null, BlockPos.ORIGIN, "up", "test")).join();
        assertNotNull(place.getFailureContext());
        assertEquals("placeBlock", place.getFailureContext().commandType());
    }

    /* ──────────────── InventoryExecutor ──────────────── */

    private final InventoryExecutor inv = new InventoryExecutor();

    @Test @DisplayName("Inventory: canHandle 支持的动作")
    void invCanHandle() {
        assertTrue(inv.canHandle("equipItem"));
        assertTrue(inv.canHandle("useItem"));
        assertTrue(inv.canHandle("dropItem"));
        assertTrue(inv.canHandle("openBlock"));
        assertTrue(inv.canHandle("closeWindow"));
        assertTrue(inv.canHandle("clickSlot"));
        assertFalse(inv.canHandle("craft"));
        assertFalse(inv.canHandle(null));
    }

    @Test @DisplayName("Inventory: null bot 返回 unable")
    void invNullBot() {
        ActionResult r = inv.submit(InventoryCommand.equipItem(null, "stone", "test")).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Inventory: null itemName 返回 unable")
    void invNullItem() {
        ActionResult r = inv.submit(new InventoryCommand(null, "equipItem", null, 0, 0, null, null)).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Inventory: 初始 FailureContext = null")
    void invInitFailureContext() {
        assertNull(inv.getFailureContext());
    }

    /* ──────────────── CraftExecutor ──────────────── */

    private final CraftExecutor craft = new CraftExecutor();

    @Test @DisplayName("Craft: canHandle craft")
    void craftCanHandle() {
        assertTrue(craft.canHandle("craft"));
        assertFalse(craft.canHandle("dig"));
        assertFalse(craft.canHandle(null));
    }

    @Test @DisplayName("Craft: null bot 返回 unable")
    void craftNullBot() {
        CraftCommand cmd = new CraftCommand(null, "minecraft:stone", "test");
        ActionResult r = craft.submit(cmd).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Craft: null itemId 返回 unable")
    void craftNullItem() {
        CraftCommand cmd = new CraftCommand(null, null, "test");
        ActionResult r = craft.submit(cmd).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Craft: 空 itemId 返回 unable")
    void craftEmptyItem() {
        CraftCommand cmd = new CraftCommand(null, "", "test");
        ActionResult r = craft.submit(cmd).join();
        assertFalse(r.success());
        assertFalse(r.executed());
    }

    @Test @DisplayName("Craft: 无效 item ID 返回 fail")
    void craftInvalidId() {
        CraftCommand cmd = new CraftCommand(null, "invalid:id!!!", "test");
        ActionResult r = craft.submit(cmd).join();
        assertFalse(r.success());
    }

    @Test @DisplayName("Craft: 初始 FailureContext = null")
    void craftInitFailureContext() {
        assertNull(craft.getFailureContext());
    }

    /* ──────────────── DomainRouter ──────────────── */

    private final DomainRouter router = new DomainRouter();

    @Test @DisplayName("Router: dispatch null 返回 null")
    void routerNullCmd() {
        assertNull(router.dispatch(null).join());
    }

    @Test @DisplayName("Router: 注册后 dispatch 成功")
    @SuppressWarnings("unchecked")
    void routerRegister() {
        router.register(new PlaceExecutor());
        DomainCommand cmd = new PlaceCommand(null, BlockPos.ORIGIN, "up", "test");
        var r = (ActionResult) router.dispatch(cmd).join();
        assertNotNull(r);
    }

    @Test @DisplayName("Router: 未注册类型抛异常")
    void routerUnregistered() {
        assertThrows(Exception.class, () -> {
            router.dispatch(new CraftCommand(null, "minecraft:stone", "test")).join();
        });
    }

    @Test @DisplayName("Router: tickAll 不抛异常")
    void routerTickAll() {
        router.register(new PlaceExecutor());
        router.register(new InventoryExecutor());
        router.register(new CraftExecutor());
        assertDoesNotThrow(() -> router.tickAll());
    }

    @Test @DisplayName("Router: getAllFailureContexts 空时返回空")
    void routerEmptyFailures() {
        assertTrue(router.getAllFailureContexts().isEmpty());
    }

    /* ──────────────── DomainCommand records ──────────────── */

    @Test @DisplayName("Command: PlaceCommand 属性")
    void placeCommandProps() {
        PlaceCommand cmd = new PlaceCommand(null, BlockPos.ORIGIN, "up", "build");
        assertEquals("placeBlock", cmd.commandType());
        assertEquals("build", cmd.reason());
    }

    @Test @DisplayName("Command: InventoryCommand 工厂方法")
    void inventoryCommandFactory() {
        InventoryCommand cmd = InventoryCommand.useItem(null, "eat");
        assertEquals("useItem", cmd.commandType());
        assertEquals("eat", cmd.reason());
    }

    @Test @DisplayName("Command: CraftCommand 属性")
    void craftCommandProps() {
        CraftCommand cmd = new CraftCommand(null, "minecraft:iron_ingot", "smelt");
        assertEquals("craft", cmd.commandType());
    }

    @Test @DisplayName("Command: BreakCommand 属性")
    void breakCommandProps() {
        BreakCommand cmd = new BreakCommand(null, BlockPos.ORIGIN, "mine");
        assertEquals("dig", cmd.commandType());
    }
}
