# AI Player Mod — 可插拔 LLM 的操作系统内核

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric%20Loader-0.19.3-blue)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Status](https://img.shields.io/badge/status-beta-yellow)](#测试版状态)

> **核心理念**：反射为中心，LLM 为编译器。6 层拦截器 + MotivationEngine(玻尔兹曼选择) + LLM 门控 = 成本收敛到 0。
> 学习是为了不学习。思考是为了不思考。

一个能在 Minecraft 中自主生存、向玩家学习、形成习惯性格的 AI 玩家。
LLM 仅用于"陌生场景"的第一次思考，之后靠本地反射（0 成本、0 API）自动执行。

---

## 🧠 设计思路

### 六层拦截器（按成本分层）

每一层存在的意义，都是让下一层不需要被调用。

| 层 | 模组组件 | 触发条件 | 成本 | 不学习的理由 |
|----|---------|---------|------|-------------|
| **L0 生存本能** | `InnateReflexRegistry` | 熔岩/虚空/HP<2 | 0 | 先天不学 |
| **L1 先天预警** | `OneShotAlarmSystem` | 玩家说过"坏" | 0 | 学一次/永远不学 |
| **L2 条件反射** | `ConditionedReflex` | 匹配已有反射 | 0 | 用进废退 |
| **L3 模仿学习** | `SocialObserver` + 贝叶斯 | 附近有人在做 | 0 | 观察→固化 |
| **L4 自组织** | 相关性检测器 | 无任何匹配 | 0 | 乱试学会 |
| **L5 本地规划** | `LocalTaskDecomposer` | 规则匹配 | 0 | 模板拆解 |
| **L6 LLM 兜底** | LLM | 以上全不命中 | $ | 最后一次思考 |

### MetaScheduler — 动机驱动的动态路由器

固定流水线（P0→P5 每个 tick 全跑一遍）已被 **元调度器** 替代：

```
MetaScheduler.tick():
  1. MotivationEngine.computeDrives()  ← 5通道并行竞争 (生存/任务/社交/好奇/谨慎)
  2. MotivationEngine.select()         ← 玻尔兹曼软最大化 (temperature 控制随机性)
  3. labelProblem(视角)                ← 贴标签（现在是哪种问题）
  4. getFlowAdjustment(ctx)            ← 升降级（该升还是降）
  5. dispatch(label, flow)             ← 分派到对应执行层 (+ LLM 门控)
```

**5 通道并行竞争 + 交叉抑制** 替代了旧的 if-else 链。选中的视角对其它通道施加 -30% 抑制，持续 5 tick。
**LLM 门控** 确保 LLM 只在 6 条件同时满足时才放行（API配置/冷却/本地可处理/标签/抑制/失败率）。

### 三层信息传递系统

| 传递类型 | 时间尺度 | 工程实现 |
|---------|---------|---------|
| **基因层** | 代际 | `BotParams` + 三规则继承（平均+减半+突变） |
| **激素层** | 秒~分钟 | `HormonalSystem`（stress/aggression/curiosity/intimacy） |
| **反射层** | 分钟~小时 | `ConditionedReflex` + `reinforce()` + `scanAndTrigger()` |

三种传递形成闭环：执行反射 → 成功/失败 → 激素浓度变化 → 视角选择偏移 → 反射固化 → 死亡 → 三规则继承给后代。

### 设计权衡

所有设计来自同一个核心矛盾：**低成本 × 像人 × 长期在线**。详见 [AGENTS.md](AGENTS.md#设计权衡记录不是原则是当前取舍) 的矛盾分解与策略表。

---

## 🧩 功能特性

### 四模块目录结构

| 模块 | 职责 | 设计参考 |
|------|------|---------|
| **cortex/** | 规划、复杂决策、语义理解 | 可替换的规划层 |
| **hippocampus/** | 记忆存储、高光回忆 | 仓库 |
| **amygdala/** | 条件反射、学习、评价、激素 | 习惯养成层 |
| **brainstem/** | 先天反射、基础动作、生存本能 | 执行层 |

### 12 原子动作

`moveTo` / `lookAt` / `dig` / `attack` / `placeBlock` / `useItem` / `equipItem` / `openBlock` / `closeWindow` / `clickSlot` / `chat` / `jump`

### 8 先天反射

`flee` / `eat` / `retreat` / `avoidLava` / `seekShelter` / `collectItem` — 全部 JSON 可配，0 API

### 社交学习

- **L1 一次预警**：玩家说"creeper危险" → 永久记住 creeper 是威胁 → 下次自动绕开
- **观察学习**：监听玩家行为 → 60s 窗口模式检测 → 3次成功自动固化
- **社交镜像**：KNN + 朴素贝叶斯 → 选择性模仿群体
- **模仿抑制**：前额叶否决有害从众（跳崖、打村民）

### 用进废退（不是 RL）

- **性格 = 反射权重的统计分布**：所有行为倾向编码在反射的 `shortTermWeight` + `longTermBaseline` 中
- **激素实时调节**：`HormonalSystem` 调节视角选择（stress/aggression/curiosity/intimacy 四个轴）
- **个体差异**：每个 AI 的 α/β 随机初始化 + 独立的学习历史 → 不同"人格"
- **休眠不删除**：低频反射标记 dormant，保留 JSON，可复活

---

## 🔧 构建

```bash
# 环境要求: JDK 21, Gradle 8+

git clone <repo-url>
cd AIPlayerMod-1.21.1-Fabric

# 编译
.\gradlew.bat build

# 运行测试 (102 tests)
.\gradlew.bat runTests

# 输出: build/libs/ai-player-mod-1.21.1.jar
```

> 由于项目路径含中文，标准 `test` 任务需使用 `runTests` 自定义任务规避 ClassNotFoundException。

---

## 🚀 部署

1. 将 `build/libs/ai-player-mod-1.21.1.jar` 放入 `mods/` 目录
2. 启动 Fabric 服务器 (Loader 0.19.3+)
3. 首次启动后，配置 API 密钥：

```
/ai setkey <your-deepseek-api-key>
```

4. 对 AI 玩家说自然语言指令（如"帮我挖10个铁矿"）
5. 查看状态：

```
/ai status          # 当前任务
/ai personality     # AI 学习参数 (α/β) 及反射统计
/ai reflexes        # 已学习的条件反射
/ai suggestions     # 触发主动建议
/ai help            # 全部指令
```

---

## ⚠️ 测试版状态

**当前版本为测试版 (beta)**，以下功能仍在开发中：

| 状态 | 说明 |
|------|------|
| ✅ | 四个脑区模块全部实现，102 tests 0 failures |
| ✅ | 六层拦截器: L0(本能)+L1(预警)+L2(条件反射)+L3(模仿)+L4(自组织)+L5(本地规划)+L6(LLM) |
| ✅ | 三层信息传递: 基因层+激素层+反射层 闭环 |
| ✅ | MetaScheduler + MotivationEngine (5通道玻尔兹曼选择 + 交叉抑制) |
| ✅ | LLM 门控 (6条件合取) + 好奇心指数衰减 (30min半衰期) |
| ✅ | OneShotAlarmSystem + HormonalSystem |
| ✅ | ConditionedReflex + 反射动力学 |
| ✅ | SocialObserver + 社交镜像 |
| ✅ | 前额叶抑制控制 (InhibitoryControl) |
| ✅ | Phase 4.5 — MotivationEngine + LLM Gate |
| 🔴 | Phase 5 — 紧急程度 (连续 urgency 信号 + 时间累积) |
| ⬜ | Phase 5 — 时间缩放 + 自组织 |
| ⬜ | Phase 6 — Multi-bot |
| ⬜ | Phase 7 — 繁衍模块 |
| ⚠️ | 寻路暂未集成到主循环（AStarPathfinder 存在但未接入） |

---

## 📁 运行时数据目录

```
minecraft/ai_memory/
├── config/               全局配置 (L0/L1)
├── thresholds/           自适应阈值配置 (L1)
└── bots/{bot_uuid}/      每个假人独立命名空间
    ├── alarms/           L1 一次预警
    ├── conditioned/      L2 条件反射库
    ├── memory/           记忆 (7天窗口)
    ├── evaluations/      玩家评价缓存
    ├── plans/            任务计划
    ├── state/            状态同步
    ├── character/        性格标签/偏好
    ├── dispatch_weights.json  L2 调度反射权重
    └── execution_logs/   执行日志
```

---

## 📚 详细文档

完整架构、设计权衡、内化指南见 [AGENTS.md](AGENTS.md) 和 [INTERNALIZATION.md](INTERNALIZATION.md)。

---

## 🧬 未来计划

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | MetaScheduler + BotContext | ✅ |
| Phase 2 | OneShotAlarmSystem + HormonalSystem | ✅ |
| Phase 3 | LocalTaskDecomposer | ✅ |
| Phase 4 | LocalChatHandler + 模板差异化 | ✅ |
| Phase 4.5 | MotivationEngine + LLM Gate + Curiosity Drive | ✅ |
| Phase 5 | 紧急程度 (连续 urgency + 时间累积) | 🔴 部分 |
| Phase 6 | 多假人 | ⬜ |
| Phase 7 | 繁衍模块 | ⬜ |

### 角色性格生成插件

自然语言描述角色性格（如"像炭治郎一样温柔但对恶鬼绝不手软"），
LLM 生成反射权重分布 JSON，丢入 `conditioned/` 即可。
任何 LLM 都胜任，迭代只需重新描述。

---

## 📄 许可证

MIT License
