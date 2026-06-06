# AI Player Mod - AGENTS.md

## 项目概览

Fabric 1.21.1 模组，通过文件存储实现持久化 AI 玩家陪伴，用于辅助测试模组效果。AI 玩家的性格（character）能根据玩家互动（行为观察 + 命令 + 聊天）逐步演化。

## 技术栈

- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.19.3
- **Fabric API**: 0.116.12+1.21.1
- **Java**: 21
- **Mod ID**: `ai-player-mod`
- **包名**: `com.izimi.aiplayermod`
- **路径导航**: 内置 A* 算法（无 Baritone 依赖）
- **JSON 序列化**: Gson（Fabric 自带）

## 关键设计决策

| 决策 | 选择 |
|---|---|
| AI 控制方式 | Fabric 内置 Bot（纯 Java） |
| Bot 假人类型 | 可见 Bot 玩家（其他玩家能看到行走/挖矿动画） |
| 记忆检索策略 | 默认缓存最近 3 天 + 玩家提问时按需检索 |
| 性格演化权重 | 行为观察 0.6 / 命令反馈 0.3 / 聊天互动 0.1 |
| 路径导航 | 内置 A* 寻路，支持跳跃/攀爬/规避危险 |
| 条件反射阈值 | 有效动作 > 3 次且 avg_effectiveness > 0.8 |

## 文件结构（运行时生成）

```
.minecraft/ai_memory/
├── tasks/
│   ├── active_task.json     # 当前执行的事务
│   └── last_task.json       # 被中断的事务
├── memories/
│   ├── day_001.mem          # 事务完成后的高光总结
│   ├── day_002.mem
│   └── latest.mem
├── skills/
│   ├── innate/              # 先天反射（只读）
│   ├── conditioned/         # 条件反射（AI 可写）
│   └── character/
│       ├── preferences.json
│       ├── habits.json
│       └── risk_tolerance.json
├── plans/                    # 复杂计划
├── state/                    # 实时状态
│   └── current.json
├── execution_logs/           # 执行日志（短期）
└── config/
    └── config.json
```

## 游戏内指令

| 指令 | 功能 | 示例 |
|---|---|---|
| `/ai <目标>` | 创建新事务 | `/ai 挖10个铁矿` |
| `/ai status` | 查看当前任务状态 | |
| `/ai cancel` | 中断当前任务 | |
| `/ai resume` | 恢复上一个任务 | |
| `/ai explore` | 开始自由探索 | |
| `/ai spawn` | 生成 AI 玩家 | |
| `/ai despawn` | 移除 AI 玩家 | |
| `/ai personality` | 查看当前个性偏好 | |
| `/ai forget <id>` | 删除指定记忆 | `/ai forget mem_001` |

## Package 结构

```
com.izimi.aiplayermod
├── AIPlayerMod.java                    # 初始化：目录创建、命令注册、事件挂载
├── AIPlayerModDataGenerator.java       # 数据生成器
├── bot/
│   ├── BotPlayer.java                  # 继承 ServerPlayerEntity 的可见假人
│   ├── BotSpawner.java                 # 生成/移除/注册到玩家列表
│   └── BotController.java              # Tick 控制循环
├── command/
│   └── AICommand.java                  # /ai 全部子命令（Brigadier）
├── memory/
│   ├── MemoryManager.java              # 时间窗口缓存 + 按需检索
│   ├── MemoryEntry.java                # 记忆数据类
│   └── MemoryQuery.java                # 自然语言触发检索
├── task/
│   ├── TaskManager.java                # CRUD + 中断/恢复
│   ├── Task.java                       # 事务数据类
│   └── TaskExecutor.java               # 事务拆解为 Skill 调用
├── skill/
│   ├── Skill.java                      # 技能接口
│   ├── SkillManager.java               # 加载/匹配技能文件
│   ├── innate/
│   │   ├── MoveSkill.java              # 导航移动
│   │   ├── DigSkill.java               # 挖掘方块
│   │   ├── AttackSkill.java            # 攻击实体
│   │   └── CraftSkill.java             # 合成物品
│   └── ConditionedReflex.java          # 条件反射检测 + 短路
├── character/
│   ├── CharacterManager.java           # 偏好核心
│   ├── Preference.java                 # 偏好数据类
│   ├── BehaviorObserver.java           # 监听玩家行为/聊天
│   └── PersonalityEvolution.java       # 加权演化算法
├── state/
│   ├── StateManager.java               # 采集 Bot 状态
│   └── PlayerState.java                # 状态数据类
├── log/
│   └── ExecutionLogger.java            # 日志写入
├── config/
│   └── ModConfig.java                  # 配置文件读写
├── navigation/
│   ├── AStarPathfinder.java            # A* 寻路核心
│   ├── NavigationController.java       # 路径跟随
│   └── BlockPosUtil.java               # 坐标工具
└── util/
    ├── JsonUtil.java                   # Gson 封装
    └── FileUtil.java                   # 路径 + 目录工具
```

## 数据结构（JSON 映射）

### 事务 (Task)
```json
{
  "task_id": "task_20260106_001",
  "type": "instant|sustained|plan",
  "goal": "挖10个铁矿",
  "status": "running|paused|completed|aborted",
  "progress": { "current_step": "mining", "completed_count": 3, "target_count": 10 },
  "context": { "position": [x, y, z], "inventory_snapshot": {}, "start_time": 123456789 },
  "parent_task_id": null,
  "preempt_policy": "ask"
}
```

### 记忆 (MemoryEntry)
```json
{
  "id": "mem_20260106_001",
  "summary": "成功挖掘10块铁矿，使用石镐，耗时5分钟",
  "key_learnings": ["铁矿集中在Y=20层", "周围有洞穴需警惕"],
  "related_skills": ["mine_iron", "navigate_cave"],
  "preferences_updated": { "iron_ore": 0.7 },
  "timestamp": 123456789,
  "game_day": 37
}
```

### 技能 (Skill)
```json
{
  "skill_id": "dig",
  "type": "innate",
  "name": "挖掘",
  "description": "挖掘面前的方块",
  "precondition": { "has_tool": true, "target_block_exists": true },
  "execution": { "api_call": "bot.dig(block)", "retry_on_fail": 3 },
  "postcondition": { "block_changed_to": "air", "item_obtained": true }
}
```

### 偏好 (Preference)
```json
{
  "preferences": [
    { "target": "diamond_ore", "valence": 0.95, "origin": "experience", "reinforcement_count": 12 }
  ],
  "last_updated": 123456789
}
```

### 实时状态 (PlayerState)
```json
{
  "timestamp": 123456789,
  "player": { "position": [x, y, z], "health": 20, "hunger": 18, "inventory": {} },
  "world": { "biome": "plains", "time_of_day": 6000, "nearby_entities": [] }
}
```

### 执行日志 (ExecutionLog)
```json
{
  "timestamp": 123456789,
  "action": "retreat_from_creeper",
  "context": { "player_health": 8, "creeper_distance": 2 },
  "outcome": "success",
  "effectiveness": 0.9
}
```

## 核心流程

### 事务生命周期
```
[玩家输入] → 创建 Task → 写入 active_task.json → BotController 逐 tick 执行
    ├── [中断] 新指令 → active → last_task，处理新指令
    ├── [恢复] "继续" → last_task → active
    └── [完成] → 生成记忆 → 更新 character → 删除事务文件
```

### 记忆轻量化
```
事务完成 → AI 生成 summary + key_learnings → 写入 day_XXX.mem → 更新 latest.mem → 删除 active_task.json
```

### 条件反射形成
```
执行 Skill → 写入 execution_logs → 定期分析（每20次或10分钟）
    → 有效性>0.8 且成功>3次 → 生成 conditioned/xxx.skill → 后续直接触发
```

### 性格演化
```
行为观察（挖矿/杀怪/聊天）+ 命令反馈 → 累计达到阈值 → 更新 preferences.json → 影响 Bot 后续决策
```
公式：`valence = base + behaviorDelta*0.6 + commandDelta*0.3 + chatDelta*0.1`

## 实施阶段

| 阶段 | 文件 | 内容 |
|---|---|---|
| **P1 基础** | AIPlayerMod, JsonUtil, FileUtil, ModConfig, AICommand | 目录创建、命令注册骨架 |
| **P2 Bot核心** | BotPlayer, BotSpawner, BotController, StateManager | 假人生成/移除、tick循环、状态采集 |
| **P3 事务系统** | Task, TaskManager, TaskExecutor | 事务CRUD、中断恢复、执行调度 |
| **P4 技能系统** | Skill, SkillManager, 4×InnateSkill, ConditionedReflex, ExecutionLogger | 技能加载执行、条件反射、日志 |
| **P5 记忆系统** | MemoryEntry, MemoryManager, MemoryQuery | 记忆生成、缓存、按需检索 |
| **P6 性格演化** | Preference, CharacterManager, BehaviorObserver, PersonalityEvolution | 行为监听、加权计算、偏好影响 |
| **P7 导航与探索** | AStarPathfinder, NavigationController, BlockPosUtil + 探索逻辑 | A*寻路、路径跟随、自主探索 |

## 构建命令

```bash
# Windows
gradlew.bat build

# 检查代码
gradlew.bat check

# 运行客户端
gradlew.bat runClient
```
