# E-Agent 架构修复与边界明确化实施计划

> **目标：** 解决域执行器架构 2/6 完成、MinecraftActionAdapter 双轨并行、静默失败、文档不同步四大问题，消除 6 处边界模糊
>
> **架构原则：** 每次改动后 337 个测试必须全部通过。不改行为，只修架构。
>
> **技术栈：** Java 21, Fabric Loom, JUnit 5, Mockito

---

## 总体路线图

```
Phase 0 ─── 快速止血（1-2 轮）
  ├── 0.1: DomainRouter 静默失败 → 明确异常
  ├── 0.2: 文档同步（ARCHITECTURE.md / DEVELOPMENT.md）
  └── 0.3: 添加"无 Executor"的编译期/运行期警告

Phase 1 ─── 域架构补全（2-3 轮）
  ├── 1.1: CombatExecutor + CombatAdapter 实现
  ├── 1.2: CraftExecutor + CraftAdapter 实现
  ├── 1.3: PlaceExecutor + PlaceAdapter 实现
  ├── 1.4: InventoryExecutor + InventoryAdapter 实现
  └── 1.5: MinecraftActionAdapter 域委派重构 + BasicActionAdapter 接口清理

Phase 2 ─── 行为层修复（1 轮）
  ├── 2.1: flee/retreat 接入 NavigationController
  └── 2.2: L0 触发模型文档化

Phase 3 ─── 边界明确化（1-2 轮）
  ├── 3.1: 域架构在 §14 组件归属表中占位
  ├── 3.2: §1/§12 双重分层的交叉引用文档化
  ├── 3.3: REFLEX_CREATE 钩子后自动生成反射配方向量
  └── 3.4: 阈值参数实验方法论文档

Phase 4 ─── 长远架构统一（2-3 轮）
  ├── 4.1: 消除"原子/复合"动作的随意分类
  ├── 4.2: 统一所有动作执行路径（DomainRouter 作为唯一入口）
  └── 4.3: 域架构的分层归属决策
```

---

## Phase 0: 快速止血

### Task 0.1: DomainRouter 静默失败 → 明确异常

**Objective:** `DomainRouter.dispatch()` 找不到对应 Executor 时，抛 `UnsupportedOperationException` 而非静默返回 null

**Files:**
- Modify: `src/main/java/com/izimi/eagent/brainstem/domain/DomainRouter.java:34-35`

**Step 1: 修改 dispatch 方法的 fallthrough 分支**

```java
// 修改前 (row 34-35):
LOGGER.warn("[DomainRouter] No executor for: {}", cmd.commandType());
return CompletableFuture.completedFuture(null);

// 修改后:
String msg = "[DomainRouter] No executor registered for command type: " + cmd.commandType();
LOGGER.error(msg);
return CompletableFuture.failedFuture(new UnsupportedOperationException(msg));
```

**Step 2: 验证编译**

```bash
cd F:\mcMods\EAgentMod-1.21.1-Fabric
./gradlew.bat compileJava 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

**Step 3: 验证测试**

调用链路上如果有测试涉及未实现的 Command（如测试中构造 CraftCommand 然后 dispatch），会因这个改动而失败。需要搜索检查。

```bash
./gradlew.bat test --tests "*Domain*" 2>&1 | tail -20
```

**Step 4: 记录风险**

- 风险：任何生产路径上如果真有无 Executor 的 Command 被调度，现在会抛异常而不是静默失败。这是期望行为——快速失败比数据损坏好。
- 回退：撤销本次改动即可。

### Task 0.2: 更新 ARCHITECTURE.md 域执行器状态标注

**Objective:** 文档中的"Stage 4 占位，抛出 UnsupportedOperationException"与实际代码同步

**Files:**
- Modify: `ARCHITECTURE.md`

**修改点 1：** `ARCHITECTURE.md` §4.1a 的表格注释

```markdown
**注**：PlaceCommand/CraftCommand/CombatCommand/InventoryCommand 为 Stage 4 占位。
- 当前行为：通过 DomainRouter.dispatch() 抛 UnsupportedOperationException（见 Task 0.1）
- Stage 4 计划：为每个 Command 实现对应的 Executor
- 当前各类型状态：
  | Command | Executor | 状态 |
  |:-------:|:--------:|:----:|
  | BreakCommand | DigExecutor | ✅ Stage 2 完成 |
  | MotionCommand | MotionExecutor | ✅ Stage 3 完成 |
  | CombatCommand | ❌ 无 | ⏳ Stage 4 待实现 |
  | CraftCommand | ❌ 无 | ⏳ Stage 4 待实现 |
  | PlaceCommand | ❌ 无 | ⏳ Stage 4 待实现 |
  | InventoryCommand | ❌ 无 | ⏳ Stage 4 待实现 |
```

**修改点 2：** §8 基本动作池表格下方注记更新

```markdown
> **注**：上表为 14 个核心原子动作。BasicActionAdapter 实际有 22 个方法 →
> 14 原子 + 8 复合。**注意**：目前只有 moveTo/lookAt/dig/jump/sprint/sneak 通过
> DomainRouter → Executor 执行链（见 §4.1a）。attack/placeBlock/craft 等其余动作
> 仍在 MinecraftActionAdapter 中直接实现，**计划在 Stage 4 迁移**。
```

### Task 0.3: 添加编译期/运行期警告

**Objective:** 帮助开发者发现 Executor 未注册的问题

**Files:**
- Modify: `src/main/java/com/izimi/eagent/brainstem/domain/DomainRouter.java`

**修改：** 在 `register()` 方法中添加冲突检测

```java
public void register(DomainExecutor<?, ?> executor) {
    if (executor == null) return;
    // 检测：是否已有同 domain type 的 executor
    for (var existing : executors) {
        if (existing.getDomainType().equals(executor.getDomainType())) {
            LOGGER.warn("[DomainRouter] Duplicate executor for domain: {}, replacing", executor.getDomainType());
        }
    }
    executors.add(executor);
}
```

```java
// 在 DomainExecutor 接口中要求 domain type
// 修改后需验证 DomainExecutor 接口兼容性
```

---

## Phase 1: 域架构补全

### Task 1.1: CombatExecutor + CombatAdapter 实现

**Objective:** 为 CombatCommand 实现执行器，封装 Minecraft 战斗逻辑

**Files:**
- Create: `src/main/java/com/izimi/eagent/brainstem/domain/CombatExecutor.java`
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`（委派 attack）

**CombatExecutor.java 设计：**

```java
package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CombatExecutor implements DomainExecutor<CombatCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int SCAN_RANGE = 8;

    private FailureContext failureContext;

    @Override
    public boolean canHandle(String commandType) {
        return "attack".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(CombatCommand command) {
        ServerPlayerEntity bot = command.bot();
        String entityName = command.entityName();

        if (bot == null) {
            return CompletableFuture.completedFuture(ActionResult.unable("attack: bot为null"));
        }

        ServerWorld world = bot.getServerWorld();
        LivingEntity target = findTarget(world, bot, entityName);

        if (target == null) {
            String reason = "附近没有" + (entityName != null ? entityName : "攻击目标");
            failureContext = new FailureContext("attack", FailureContext.FailureReason.NO_TARGET, reason, "靠近敌人位置");
            return CompletableFuture.completedFuture(ActionResult.unable(reason));
        }

        // 锁定视角
        double px = target.getX();
        double py = target.getEyeY();
        double pz = target.getZ();
        double dx = px - bot.getX();
        double dy = py - (bot.getY() + bot.getStandingEyeHeight());
        double dz = pz - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);

        double distSq = bot.squaredDistanceTo(target);
        if (distSq > 25.0) {
            // 追击
            Vec3d dir = target.getPos().subtract(bot.getPos()).normalize().multiply(0.15);
            bot.setVelocity(new Vec3d(dir.x, 0.08, dir.z));
            bot.velocityModified = true;
            return CompletableFuture.completedFuture(ActionResult.partial(0.4, "追击中"));
        }

        bot.swingHand(Hand.MAIN_HAND);
        bot.attack(target);
        failureContext = null;
        return CompletableFuture.completedFuture(ActionResult.partial(0.7, "攻击"));
    }

    private LivingEntity findTarget(ServerWorld world, ServerPlayerEntity bot, String entityName) {
        List<? extends LivingEntity> all = world.getEntitiesByClass(
                LivingEntity.class,
                bot.getBoundingBox().expand(SCAN_RANGE),
                e -> e.isAlive() && e != bot);

        if (entityName != null && !entityName.isEmpty()) {
            for (var e : all) {
                String id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
                if (id.toLowerCase().contains(entityName.toLowerCase())) return e;
            }
            return null;
        }
        return all.isEmpty() ? null : all.stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)))
                .orElse(null);
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return failureContext; }
}
```

**MinecraftActionAdapter.attack() 修改：**

```java
// 修改前 (行 94-118): 直接在 Adapter 中实现
// 修改后:
private final CombatExecutor combatExecutor = new CombatExecutor();
public CombatExecutor getCombatExecutor() { return combatExecutor; }

@Override
public ActionResult attack(ServerPlayerEntity bot, String entityName) {
    return combatExecutor.submit(new CombatCommand(bot, entityName, "attack")).join();
}
```

**注册到 DomainRouter（在初始化处）：**
```java
domainRouter.register(new CombatExecutor());
**Verification:**
- `./gradlew.bat test --tests "*Combat*"` — 可能无专属测试，需确认现有 DecisionQualityTest 中涉及攻击的场景
- `./gradlew.bat compileJava` — 编译通过
- 冒烟：`CombatCommand` + `DomainRouter.dispatch()` → 返回成功
</details>

### Task 1.2: CraftExecutor 实现

**Objective:** 从 MinecraftActionAdapter 中提取合成逻辑到 CraftExecutor

**Files:**
- Create: `src/main/java/com/izimi/eagent/brainstem/domain/CraftExecutor.java`
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`

**策略：** 将 Adapter 中现有的 `craft()` → `findCraftingRecipe()` → `openCraftingInterface()` → `placeItemsInGrid()` → `collectCraftResult()` 全套逻辑完整迁移，保持内部 helper 方法不变。

```java
public class CraftExecutor implements DomainExecutor<CraftCommand, ActionResult> {

    private static final int CT_RESULT = 0;
    private static final int CT_GRID_START = 1;
    private static final int CT_GRID_END = 9;
    private static final int CT_GRID_WIDTH = 3;
    private static final int CT_INV_START = 10;
    private static final int CT_HOTBAR_START = 37;
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
        // 完整迁移 MinecraftActionAdapter.craft() 行 372-392 的逻辑
        // + 依赖的 helper 方法 (findCraftingRecipe, openCraftingInterface,
        //   placeItemsInGrid, collectCraftResult, cleanupCraft,
        //   countCraftable, moveIngredientToSlot, placeIngredients,
        //   placeShapedIngredients, placeShapelessIngredients)
        // ... (~200 行代码)
    }
}
```

**注意：** CraftExecutor 依赖 Minecraft 配方系统（RecipeManager, Ingredient, ScreenHandler），需要确保引用完整。此处不做逐行复制，在实现时直接引用迁移。

### Task 1.3: PlaceExecutor 实现

**Objective:** 从 MinecraftActionAdapter 中提取放置逻辑到 PlaceExecutor

**Files:**
- Create: `src/main/java/com/izimi/eagent/brainstem/domain/PlaceExecutor.java`
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`

**策略同 Task 1.2：** 迁移 `placeBlock()` 的 20 行逻辑，包括 `parseFace()` 的 Direction 解析。

### Task 1.4: InventoryExecutor 实现

**Objective:** 从 MinecraftActionAdapter 中提取物品栏操作到 InventoryExecutor

**Files:**
- Create: `src/main/java/com/izimi/eagent/brainstem/domain/InventoryExecutor.java`
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`

**InventoryCommand 的 factory methods（equipItem/useItem/dropItem/openBlock/closeWindow/clickSlot）** 不应全部走同一个 Executor 方法。建议设计为：

```java
public class InventoryExecutor implements DomainExecutor<InventoryCommand, ActionResult> {

    @Override
    public boolean canHandle(String commandType) {
        return Set.of("equipItem", "useItem", "dropItem", "openBlock", "closeWindow", "clickSlot")
                .contains(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(InventoryCommand command) {
        return CompletableFuture.completedFuture(switch (command.action()) {
            case "equipItem" -> equipItem(command.bot(), command.itemName());
            case "useItem" -> useItem(command.bot());
            case "dropItem" -> dropItem(command.bot(), command.slot());
            case "openBlock" -> openBlock(command.bot(), command.blockPos());
            case "closeWindow" -> closeWindow(command.bot());
            case "clickSlot" -> clickSlot(command.bot(), command.slot(), command.button());
            default -> ActionResult.fail("未知 Inventory 动作: " + command.action());
        });
    }

    // ... 每个子方法的实现（从 MinecraftActionAdapter 迁移）
}
```

### Task 1.5: MinecraftActionAdapter 域委派重构 + BasicActionAdapter 清理

**Objective:** Adapter 对所有动作的调用统一经 DomainRouter

**Files:**
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/BasicActionAdapter.java`
- Modify: 所有创建 Adapter 实例的初始化代码

**BasicActionAdapter 接口清理：**

```java
// 修改前 — 暴露内部实现细节
public interface BasicActionAdapter {
    DigExecutor getDigExecutor();
    MotionExecutor getMotionExecutor();
    // 其实没必要暴露这些
}

// 修改后 — 只暴露行为
public interface BasicActionAdapter {
    ActionResult moveTo(ServerPlayerEntity bot, BlockPos target);
    ActionResult lookAt(ServerPlayerEntity bot, double x, double y, double z);
    ActionResult dig(ServerPlayerEntity bot, BlockPos target);
    ActionResult attack(ServerPlayerEntity bot, String entityName);
    ActionResult placeBlock(ServerPlayerEntity bot, BlockPos pos, String face);
    ActionResult useItem(ServerPlayerEntity bot);
    ActionResult equipItem(ServerPlayerEntity bot, String itemName);
    ActionResult openBlock(ServerPlayerEntity bot, BlockPos pos);
    ActionResult closeWindow(ServerPlayerEntity bot);
    ActionResult clickSlot(ServerPlayerEntity bot, int slot, int button);
    ActionResult craft(ServerPlayerEntity bot, String itemId);
    ActionResult chat(ServerPlayerEntity bot, String message);
    ActionResult jump(ServerPlayerEntity bot);
    ActionResult flee(ServerPlayerEntity bot, double speed);
    ActionResult eat(ServerPlayerEntity bot);
    ActionResult retreat(ServerPlayerEntity bot, double speed);
    ActionResult avoidLava(ServerPlayerEntity bot, double speed);
    ActionResult seekShelter(ServerPlayerEntity bot, double speed);
    ActionResult collectItem(ServerPlayerEntity bot, double speed);
    ActionResult sneak(ServerPlayerEntity bot, boolean sneaking);
    ActionResult sprint(ServerPlayerEntity bot, boolean sprinting);
    ActionResult dropItem(ServerPlayerEntity bot, int slot);
    void stopNavigation(UUID botId);
    // 移除 getDigExecutor/getMotionExecutor
}
```

**MinecraftActionAdapter 重构：**

```java
public class MinecraftActionAdapter implements BasicActionAdapter {

    private final DomainRouter domainRouter;

    // Executor 实例
    private final DigExecutor digExecutor = new DigExecutor();
    private final MotionExecutor motionExecutor = new MotionExecutor();
    private final CombatExecutor combatExecutor = new CombatExecutor();
    private final CraftExecutor craftExecutor = new CraftExecutor();
    private final PlaceExecutor placeExecutor = new PlaceExecutor();
    private final InventoryExecutor inventoryExecutor = new InventoryExecutor();

    public MinecraftActionAdapter() {
        this.domainRouter = new DomainRouter();
        domainRouter.register(digExecutor);
        domainRouter.register(motionExecutor);
        domainRouter.register(combatExecutor);
        domainRouter.register(craftExecutor);
        domainRouter.register(placeExecutor);
        domainRouter.register(inventoryExecutor);
    }

    // 所有操作统一走 domainRouter
    @Override
    public ActionResult attack(ServerPlayerEntity bot, String entityName) {
        return domainRouter.dispatch(new CombatCommand(bot, entityName, "attack")).join();
    }

    @Override
    public ActionResult placeBlock(ServerPlayerEntity bot, BlockPos pos, String face) {
        return domainRouter.dispatch(new PlaceCommand(bot, pos, face, "place")).join();
    }

    @Override
    public ActionResult craft(ServerPlayerEntity bot, String itemId) {
        return domainRouter.dispatch(new CraftCommand(bot, itemId, "craft")).join();
    }

    // flee/retreat/seekShelter/collectItem/avoidLava/eat — 这些是复合动作，
    // 不是 DomainCommand 的域。在 Phase 2 中迁移到 NavigationController。
    // 暂时保留在 Adapter 中的直接实现，不做域委派。
}
```

**验证：**
```bash
./gradlew.bat compileJava
./gradlew.bat test
# Expected: 337 tests all passed
```

---

## Phase 2: 行为层修复

### Task 2.1: flee/retreat 接入 NavigationController

**Objective:** flee 逃跑使用寻路系统而非 raw velocity

**Files:**
- Modify: `src/main/java/com/izimi/eagent/brainstem/adapter/MinecraftActionAdapter.java`
- (可能) Modify: `src/main/java/com/izimi/eagent/brainstem/navigation/NavigationController.java`

**设计方案：**

```java
// 修改前 — velocity 直接操作
@Override
public ActionResult flee(ServerPlayerEntity bot, double speed) {
    Vec3d away = fleeDirection(bot);
    bot.setVelocity(away.multiply(speed));
    bot.velocityModified = true;
    bot.jump();
    return ActionResult.success("flee");
}

// 修改后 — 先算一个远离威胁的目标点，然后走 moveTo
@Override
public ActionResult flee(ServerPlayerEntity bot, double speed) {
    Vec3d away = fleeDirection(bot);
    if (away == null) {
        return ActionResult.partial(0.3, "flee: 没有明显威胁方向");
    }
    BlockPos fleeTarget = new BlockPos(
        (int)(bot.getX() + away.x * 10),
        (int)bot.getY(),
        (int)(bot.getZ() + away.z * 10)
    );
    return moveTo(bot, fleeTarget);
}
```

**紧急帧设计：** 为了兼顾 L0 的实时性需求，第一帧（当 tick 检测到熔岩/虚空）允许一次 velocity 闪避，后续帧走 moveTo 寻路。

```java
@Override
public ActionResult flee(ServerPlayerEntity bot, double speed) {
    // 第一帧：velocity 紧急闪避
    if (!navigationController.isNavigating(bot.getUuid())) {
        Vec3d away = fleeDirection(bot);
        if (away != null) {
            bot.setVelocity(away.multiply(speed));
            bot.velocityModified = true;
            bot.jump();
        }
    }
    // 后续帧：寻路到安全位置
    Vec3d away = fleeDirection(bot);
    if (away == null) return ActionResult.partial(0.1, "flee: 方向不确定");
    BlockPos fleeTarget = new BlockPos(
        (int)(bot.getX() + away.x * 10),
        (int)bot.getY(),
        (int)(bot.getZ() + away.z * 10)
    );
    return motionExecutor.moveTo(bot, fleeTarget);
}
```

### Task 2.2: L0 触发模型文档化

**Objective:** 在 ARCHITECTURE.md 中明确 L0 的触发机制

**修改** `ARCHITECTURE.md` §1 六层拦截器表格：

```markdown
| **L0** 生存本能 | `InnateReflexRegistry` | 熔岩/虚空/HP<2 | 0 |

> **L0 触发机制**：L0 反射不是事件驱动的中断，而是在 MetaScheduler.tick() 的
> `executeHabitLayerWithGating()` 阶段被逐项扫描调用的。这意味着即使 bot 掉入虚空，
> 也要等下一个 tick（≤50ms）才能触发逃生反应。在 50ms 尺度上这个延迟对生存场景可接受。
> 未来如需要亚 tick 响应，需引入 Fabric 事件监听器（`ServerPlayerEntity.ON_DEATH` 等）。
```

---

## Phase 3: 边界明确化

### Task 3.1: 域架构在 §14 组件归属表中占位

**修改** `ARCHITECTURE.md` §14 表格末尾：

```markdown
| 组件 | 归属层 | 存储位置 |
|:----:|:------:|---------|
| ... | ... | ... |
| DomainRouter | 待定 (brainstem/domain) | 内存 |
| DigExecutor | 待定 (brainstem/domain) | 内存 |
| MotionExecutor | 待定 (brainstem/domain) | 内存 |
| CraftExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 |
| CombatExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 |
| PlaceExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 |
| InventoryExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 |
```

### Task 3.2: §1/§12 双重分层的交叉引用

**修改** `ARCHITECTURE.md` §14 开头的注释放大一倍：

```markdown
> ⚠️ **重要：双分类体系注意**
> 本文档使用两套互不兼容的分类维度：
> - **§1 运行时拦截层 (L0-L6)：** 按执行时机和触发条件分类
>   - L0=生存本能, L1=先天预警, L2=条件反射, L3=模仿学习, L4=自组织, L5=本地规划, L6=LLM
> - **§12/§14 硬编码分级：** 按可配置性/可学习性/可遗传性分类
>   - L0=Kernel(永硬编码), L1=Config(可配置), L2=Scheduler, L3=Knowledge, L4=Reflexes
> - **ConditionedReflex 在两种分类中位置不同：**
>   - §1 运行时 → L2（条件反射执行时机）
>   - §12/§14 分级 → L4（可学习可遗传）
> - **讨论归属时请标注分类体系**，例如"在 §1 运行时视角下，ConditionedReflex 属 L2"
```

### Task 3.3: REFLEX_CREATE 后自动生成反射配方向量

**Objective:** 新反射被 LLM 创建后，自动拥有可用的 CognitiveControl 配方向量

**Files:**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/MetaScheduler.java`（processTemplateResult）
- Modify: `src/main/java/com/izimi/eagent/cortex/prefrontal/ReflexRecipe.java`
- Modify or check: `reflex_recipes.json`

**设计方案：**

在 `MetaScheduler.processTemplateResult()` 的 REFLEX_CREATE 分支中，在固化反射后添加：

```java
// 在 processTemplateResult() 中，REFLEX_CREATE 分支最后追加：
// 自动生成反射配方向量
String action = firstStep.action();  // 第一步的动作类型
ReflexRecipe defaultRecipe = generateDefaultRecipe(reflexId, action);
cognitiveControl.registerRecipe(defaultRecipe);

// 写入 reflex_recipes.json（持久化）
```

```java
private ReflexRecipe generateDefaultRecipe(String reflexId, String action) {
    double ne = 0.5, da = 0.5, serotonin = 0.5, ach = 0.5;
    // 基于动作类型推断初始向量
    switch (action) {
        case "attack" -> { ne = 0.7; da = 0.6; serotonin = 0.2; ach = 0.8; }
        case "dig" -> { ne = 0.4; da = 0.5; serotonin = 0.4; ach = 0.7; }
        case "moveTo", "flee" -> { ne = 0.8; da = 0.3; serotonin = 0.3; ach = 0.4; }
        case "eat" -> { ne = 0.3; da = 0.5; serotonin = 0.7; ach = 0.3; }
        case "craft", "placeBlock" -> { ne = 0.3; da = 0.6; serotonin = 0.6; ach = 0.8; }
        default -> { /* 保持默认 0.5 */ }
    }
    return new ReflexRecipe(reflexId, new NeuroState(ne, da, serotonin, ach),
            Map.of(), DEFAULT_SAFETY_DISTANCE, 1.0);
}
```

**验证：**
```bash
./gradlew.bat test --tests "*CognitiveControlTest*" --tests "*MetaSchedulerCognitiveControlTest*"
# Expected: 新增测试验证新反射有 recipe
```

### Task 3.4: 阈值参数实验方法论文档

**修改** `THEORY.md` 或创建 `THRESHOLDS.md`：

```markdown
# 核心阈值参数文档

## 原则
本系统的阈值分为三类：
1. **e-based**（有理论基础）：1/e ≈ 0.3679 探索/利用切割、63.2% 执行比
2. **启发式**（工程直觉折衷）：0.5 NE 威胁阈值、0.6 显著性门控
3. **可调**（运行时性能可感知）：SAFETY_DISTANCE_THRESHOLD 等

## 当前所有阈值与来源

| 阈值 | 值 | 类型 | 来源 |
|:----:|:--:|:----:|:----:|
| 探索/利用切割 | 1/e ≈ 0.3679 | e-based | 最优停止理论 |
| 执行比 | 63.2% | e-based | 任务切换 |
| 抢占阈值 | 1/e | e-based | 优先级增量 |
| 收敛判断 | 1/e | e-based | KL 散度边界 |
| NE 威胁阈值 | 0.5 | 启发式 | 假设：NE 中值分高低 |
| 显著性门控 | 0.6 | 启发式 | 实验观察值 |
| DA 攻击前提 | ≥ 0.4 | 启发式 | DA 中上激活 |
| 5-HT 抑制强度 | 0.6 | 启发式 | 弱抑制 |
| 攻击刹车上限 | 0.9 | 工程约束 | 不彻底否决 |

## 如何调优
1. 运行挑战系统 `/ai challenge start 7`
2. 调整单个阈值后重新对比 LegacyBot vs NewBot 得分
3. 记录得分变化到 `eagent/threshold_experiments/` 目录
...
```

---

## Phase 4: 长远架构统一

### Task 4.1: 消除"原子/复合"动作分类

**Objective:** 决定"原子动作"和"复合动作"的设计区分是否还需要存在

**Files:**
- Modify: `ARCHITECTURE.md` §8 基本动作池
- Modify: `BasicActionAdapter.java`

**决策：** 在域架构完成后，`BasicActionAdapter` 不再是"原子动作池"，而是"面向域执行器的门面"：

```markdown
## §8 动作执行模型（重构后）

所有 Minecraft 动作通过 DomainCommand → DomainRouter → DomainExecutor 执行。

| Domain | Command | Executor | 职责 |
|:------:|:-------:|:--------:|------|
| Break | BreakCommand | DigExecutor | 挖矿、破方块 |
| Motion | MotionCommand | MotionExecutor | 移动、跳跃、潜行、冲刺 |
| Combat | CombatCommand | CombatExecutor | 攻击实体 |
| Craft | CraftCommand | CraftExecutor | 合成物品 |
| Place | PlaceCommand | PlaceExecutor | 放置方块 |
| Inventory | InventoryCommand | InventoryExecutor | 物品操作、容器交互、聊天 |

每个 DomainCommand 是 Java record，每个 DomainExecutor 是独立的类，通过 DomainRouter
统一分发。Adapter 只做方法签名适配和并发调度，不包含动作实现逻辑。
```

移除所有"14 原子 + 8 复合"的过时分类标注。

### Task 4.2: 统一所有动作执行路径

**Objective:** 消除任何不走 DomainRouter 的动作执行路径

**Files:**
- Modify: `MinecraftActionAdapter.java` — 剩余的动作（flee, retreat, avoidLava, seekShelter, collectItem, eat, chat）全部通过 DomainRouter

**策略：** 对于不适合 DomainCommand 的辅助动作（如 eat/flee），有两种选择：
1. 扩展 DomainCommand 的 `sealed` 层次（增加 Movement 子域）
2. 使用 ActionChain 组合原子 Command

建议走方案 1，因为 sealed interface 的扩展成本低。

### Task 4.3: 域架构分层归属决策

**Objective:** 最终确定 domain/ 包在架构中的层级归属

| 选项 | 归属层 | 理由 |
|:----:|:------:|------|
| A | L2 (调度层) | Executor 只执行，不决策 |
| B | L4 (条件反射层) | Excutor 是条件反射的执行体 |
| C | 骨架 (infra) | 跨层通用工具 |

**建议：** 选择 B（L4 条件反射层），因为 Executor 的执行和 ConditionedReflex 的原子步骤执行本质相同，合并后简化架构图：

```
L2 调度 (MetaScheduler) → 决定"做什么"
L4 执行 (DomainExecutor) → 决定"怎么做"
```

在 §14 中标注。

---

## 验证策略

### 每次改动后的验证流程

```bash
# 1. 编译
./gradlew.bat compileJava

# 2. 运行全部测试
./gradlew.bat test

# 3. 如果有新增测试文件
./gradlew.bat test --tests "*<NewTest>*"

# 4. 构建完整 jar
./gradlew.bat build
```

### 冒烟测试

```bash
# 启动 Fabric 服务端
# 生成 bot
/ai spawn test_bot
# 测试各域操作
/ai bot test_bot attack zombie
/ai bot test_bot craft crafting_table
/ai bot test_bot place
/ai status
```

---

## 风险与回退

| 风险 | 概率 | 影响 | 缓解 |
|:----:|:----:|:----:|------|
| Phase 1 Executor 迁移遗漏 | 中 | 动作不可用 | 完整的单元测试覆盖每个 Command 类型 |
| DomainRouter 抛异常破坏现有流程 | 低 | Bot 卡住 | 确认当前没有无 Executor 的 Command 在生产路径上 |
| 337 测试覆盖不到生产路径 | 低 | 回归 | 部署前做冒烟测试 |
| flee 改寻路后反应变慢 | 中 | Bot 生存率下降 | 保留第一帧 velocity 紧急帧 |

---

## 执行顺序摘要

```
Phase 0.1 — DomainRouter 异常化                    [1 文件, 3 行改动]
Phase 0.2 — 文档同步                              [1 文件, 文档改动]
Phase 1.1 — CombatExecutor                        [1 新文件 + 1 修改]
Phase 1.2 — CraftExecutor                         [1 新文件 + 1 修改]
Phase 1.3 — PlaceExecutor                         [1 新文件 + 1 修改]
Phase 1.4 — InventoryExecutor                     [1 新文件 + 1 修改]
Phase 1.5 — Adapter 重构 + 接口清理                [2 文件, 核心改动]
Phase 2.1 — flee/retreat 寻路                      [1 文件]
Phase 2.2 — L0 文档化                              [1 文件, 文档]
Phase 3.1 — 域架构归属表                            [1 文件, 文档]
Phase 3.2 — 双分类交叉引用                          [1 文件, 文档]
Phase 3.3 — 自动配方生成                           [2 文件, ~30 行代码]
Phase 3.4 — 阈值参数字档                            [1 新文件]
Phase 4.1 — 原子/复合分类消除                       [1 文件, 文档]
Phase 4.2 — 统一执行路径                            [1 文件]
Phase 4.3 — 分层归属决策                            [1 文件, 文档]

总计：~12 个文件改动，6 个新文件
```
