# E-Agent — 测试进度

> 手动测试清单 & 边界验证追踪。参见 [DEVELOPMENT.md](./DEVELOPMENT.md)（单元测试）、[AGENTS.md](./AGENTS.md)（架构原则）。

---

## 调试准备

测试前确认日志级别可见：

```
# 方案 A：Fabric 日志级别
-Dfabric.log.level=debug
# 方案 B：本 Mod 日志全开
-Dorg.slf4j.simpleLogger.log.com.izimi.eagent=debug
# 方案 C：抓包计数（最可靠）
Fiddler / Charles / Wireshark 过滤 deepseek.com API 调用
```

| 监控指标 | 获取方式 |
|---------|----------|
| LLM API 调用次数 | 方案 C 抓包 / DeepSeekClient 日志 count |
| **L0 层拦截** | `[MetaScheduler] L0 reflex: {id} critical={bool}` |
| **L0.5 否决** | `[MetaScheduler] P0.5 veto safety: {id}` |
| **L1 预警** | `[MetaScheduler] Level2 threat: {alarmId}` |
| **L2 习惯** | `[BotController] P1固化反射: {skillId}` |
| **L3 任务** | `[BotController] P2子任务执行: {goal}` |
| **L4 自动触发** | `[BotController] P3条件反射自动触发: {skillId}` |
| **L5 本地计划** | `[MetaScheduler] CortexLocal 从Plan创建任务` |
| **L6 LLM 路由** | `[MetaScheduler] Dispatch: CORTEX_LLM ({reason})` |
| **LLM 被拒** | `[MetaScheduler] LLM gate denied: {label} {flow}` |
| 调用间隔 | 相邻两次 API 请求时间戳 |
| Prompt 内容 | 方案 C 抓请求体 |

---

## ❓ 图例

- `❌` — 已知问题，待修复
- `⚠️` — 可疑行为，需确认
- `?` — 边界情况，不一定能在游戏中复现

---

## 1. 基础接入

### 1.1 API Key

- [ ] `/ai setkey sk-xxx` → `AI模式已激活 (DeepSeek)`
- [ ] `/ai setkey sk-xxx`（带尾随空格）→ 同上（key.trim() 通过）
- [ ] `/ai setkey`（无参数）→ 语法错误提示
- [ ] `/ai setkey clear` → `API密钥已清除，退回规则引擎模式`
- [ ] 清除后 `/ai apikey` → `规则引擎模式`
- [ ] 清除后发指令 → Bot 本地处理（无 API 调用）
- [ ] 重新设回 key → 恢复 AI 模式
- [ ] `/ai apikey` → 正确显示当前模式

### 1.2 模型切换

- [ ] `/ai model` → 显示当前模型名
- [ ] `/ai model gpt-4o` → `AI模型已设置为: gpt-4o`
- [ ] 切换后发指令 → 使用新模型
- [ ] 重启服务端 → `/ai model` 仍显示 `gpt-4o`（持久化校验）
- [ ] `/ai model`（不存在的模型名）→ 设置成功，后续调用失败（无熔断）
- [ ] ⚠️ `/ai model` 与 `/ai setkey` 无依赖顺序错误

### 1.3 api_key.json 文件

- [ ] 删除 `run/ai_memory/config/api_key.json` → 系统自动生成默认文件
- [ ] 手动写入非法 JSON → 系统自动覆盖重建
- [ ] ❌ 文件权限只读 → save() 静默失败，报错无反馈

---

## 2. 假人管理

### 2.1 Spawn

- [ ] `/ai spawn` → 默认名 "E-Agent"，生成在玩家前方 3 格
- [ ] `/ai spawn bot1` → 生成 "bot1"
- [ ] 生成两次同名 bot（如 `/ai spawn bot1` 两次）→ 行为确认
- [ ] 在空中 spawn → 找地面逻辑（最多下探 20 格）
- [ ] `/ai list` → 显示 bot 列表，`[工作中]` 或 `[空闲]`

### 2.2 Despawn

- [ ] `/ai despawn bot1` → "AI已移除: bot1"
- [ ] `/ai despawn`（无参数）→ 移除最近 bot
- [ ] despawn 不存在的 bot → "未找到AI: botName"
- [ ] despawn 后立即 `/ai list` → 列表为空
- [ ] despawn 后重新 spawn 同名 bot → 新 UUID，新身份证

### 2.3 Status

- [ ] `/ai status` → 显示最近 bot 当前任务（或无任务）
- [ ] `/ai bot bot1 status` → 指定 bot 状态
- [ ] 无 bot 时 `/ai status` → "没有活动的AI"

### 2.4 Cancel / Resume / Explore

- [ ] 执行中 `/ai cancel` → "任务已中断"
- [ ] 中断后 `/ai resume` → "已恢复上一个任务"
- [ ] 无任务可恢复 → "没有可恢复的任务"
- [ ] `/ai explore` → 创建探索任务
- [ ] 无 bot 时 cancel/resume/explore → "没有活动的AI"

### 2.5 Personality / Suggestions

- [ ] `/ai personality bot1` → 显示 α、β、反射数
- [ ] `/ai suggestions bot1` → 触发闲聊建议
- [ ] Bot 未 spawn 时 suggestions → "没有活动的AI"

---

## 3. 命令路由

### 3.1 @bot 路由

- [ ] `@bot1 挖10个铁矿` → bot1 收到并创建任务
- [ ] `@Bot1 挖10个铁矿`（大小写）→ 区分大小写，可能失败
- [ ] `@不存在Bot 你好` → 回退到 idleBrain 路由
- [ ] `@bot1`（无消息体）→ 静默丢弃
- [ ] `@`（仅 @ 符号）→ 回退
- [ ] Bot 未 spawn 时 `@bot1 xxx` → 回退

### 3.2 /ai bot 子命令

- [ ] `/ai bot bot1 status` → 子命令，显示状态
- [ ] `/ai bot bot1 cancel` → 子命令，取消任务
- [ ] `/ai bot bot1 resume` → 子命令，恢复任务
- [ ] `/ai bot bot1 帮我挖矿` → 贪婪字符串，创建任务
- [ ] ⚠️ `/ai bot bot1 status123` → 不匹配字面子命令，被贪婪捕获为任务目标

### 3.3 根路由

- [ ] `/ai 挖10个铁矿` → 无 @ 时路由最近 bot
- [ ] `/ai`（纯命令）→ 提示帮助
- [ ] `/ai help` → 显示全部命令列表

---

## 4. 自然语言理解

### 4.1 TemplateMatcher 分类

- [ ] "挖石头" → TASK_PLAN
- [ ] "打怪" → TASK_PLAN
- [ ] "我要5个铁" → TASK_PLAN（数字+个匹配）
- [ ] 含具体名词: "铁矿在哪里" → TASK_PLAN（concreteNoun命中）
- [ ] "学挖石头" → REFLEX_CREATE
- [ ] "如果遇到怪物就打" → REFLEX_CREATE
- [ ] "你好" → CHAT_RESPONSE
- [ ] "谢谢" → CHAT_RESPONSE
- [ ] "喵" → CHAT_RESPONSE
- [ ] "怎么做剑" → CLARIFICATION
- [ ] "这是什么？" → CLARIFICATION（? 匹配）
- [ ] ⚠️ "今天天气不错"（含"天"命中 concreteNoun）→ 误判为 TASK_PLAN
- [ ] "呵呵"（无匹配）→ 回退 CHAT_RESPONSE

### 4.2 边界

- [ ] 空消息 → 无响应
- [ ] 仅标点 "???" → 可能触发 CLARIFICATION
- [ ] 超长消息（>1000 字符）→ 无截断，可能撑爆 prompt
- [ ] Unicode 特殊字符 → 正常返回

---

## ⭐ B1 — 层间拦截边界

> **核心验证**：L0(反射)→L1(预警)→L2(习惯)→L3(社交)→L4(任务)→L5(计划)→L6(LLM)
> 重复任务应仅第 1 次调 L6，后续被 L0-L5 拦截

- [ ] `/ai spawn`，发送 "挖10个橡木" → 记录首次 API 调用（L6）
- [ ] 观察第 2~10 次挖矿 → 由 ConditionedReflex 拦截 → API 调用数不增长
- [ ] ⚠️ 若每次挖矿都触发 API → BUG: 反射未固化或情境匹配失效
- [ ] 完成后发 "继续挖橡木" → 复用已有反射，0 次 API
- [ ] 发 "挖白桦木"（同 `dig_log` 类别）→ 因 spillover(30%) 同类反射驱动，API ≤ 1
- [ ] 发 "去打僵尸"（完全新类别）→ 首次 L6，后续 L0-L5
- [ ] `/ai forget 记忆ID` 删除关联记忆 → 固化反射不受影响，仍 L0 拦截
- [ ] 重启服务端 → 固化反射从文件恢复，无需重新学习

**监控手段**：抓包过滤 `deepseek.com/v1/chat/completions` + 日志 `[BotController] P3条件反射自动触发`

---

## ⭐ B2 — 记忆与技能边界

> **验证**：MemoryManager(声明性:是什么) ≠ ConditionedReflex(程序性:怎么做)

### B2.1 记忆检索不触发动作

- [ ] 学习 "看到钻石矿→用精准采集挖"（固化反射）
- [ ] 之后问 "钻石矿在哪" → MemoryManager 回答位置 → ❌ 不应触发挖矿动作
- [ ] ⚠️ 若问位置时 bot 开始挖矿 → BUG: 声明性/程序性未分离

### B2.2 执行技能不需 LLM

- [ ] 问位置后手动走到钻石矿 → 自动触发已固化的精准采集反射
- [ ] ⚠️ 若挖矿前又调一次 LLM → BUG: ConditionedReflex 未接管

---

## ⭐ B3 — 统计与决策边界

> **验证**：BayesianModule(统计) 不干预 L0-L5 实时决策门控

- [ ] 在高失败率环境（如悬崖边频繁跌落）多次执行 "去高地"
- [ ] 观察：每次决策仍首先尝试 L0-L5 反射
- [ ] ⚠️ 若 AI 因过去失败而犹豫/僵住不执行 → BUG: 统计干扰了底层决策
- [ ] 可控性指数 > 0.3 → L1/L3 需要贝叶斯验证，不应直接 bypass

---

## ⭐ B4 — DAG 与 ReflexChain 依赖边界

> **验证**：任务图 → 执行链正确转换，失败时五阶段回退

- [ ] 下达复合任务: "先砍3块橡木，再合成木镐，最后去Y=11层"
- [ ] 故意缺少工作台，让 "合成木镐" 失败
- [ ] 观察：触发五阶段回退 → 回溯上游去先做工作台
- [ ] ⚠️ 若失败后任务链断裂或无限重试 → 依赖解析/回退逻辑需加强
- [ ] ⚠️ 若跳过合成直接尝试去 Y=11（无镐可挖）→ DAG 依赖未执行
- [ ] ⚠️ DAG 中瓶颈检测是否正确标记 "合成木镐" 为 bottleneck

---

## ⭐ B5 — 模板填空边界

> **验证**：LLM 只填充必要 JSON 槽位，不处理完整协议

### B5.1 格式混乱输入

- [ ] 输入残缺指令 "挖"
- [ ] 输入多语言混合 "mine 铁矿 and craft 镐"
- [ ] 输入仅感叹词 "！"
- [ ] 含代码块内容反注入
- [ ] ⚠️ 若系统崩溃或仍向 LLM 发送完整原始指令 → BUG

### B5.2 LLM 输出校验

- [ ] LLM 返回非法 JSON（字段名错误）→ 被 Gson 静默忽略 → 任务回退
- [ ] ❌ LLM 返回非数组对象（如 `{}` 代替 `[]`）→ `fromJson` 返回 null → 静默丢弃

---

## ⭐ B6 — 反射包跨项目移植

> **验证**：知识迁移机制

### B6.1 导出导入

- [ ] Bot A 固化了 "挖掘铁矿链" → 导出: `/ai reflexpack export botA iron_mining`
- [ ] `/ai reflexpack list` → 能看到 `iron_mining`
- [ ] spawn 全新 Bot B → `/ai reflexpack import botB iron_mining`
- [ ] 给 Bot B 下达 "挖铁矿"
- [ ] 观察：Bot B 应在 ≤3 次试错内成功（Bayesian 收敛验证）
- [ ] ⚠️ 若导入后无效果或仍需从零调 LLM → BUG: ReflexPackManager 未正确加载

### B6.2 reset vs merge

- [ ] `import botB iron_mining reset` → 冷启动，无条件覆盖
- [ ] `import botB iron_mining`（无 reset）→ merge 模式，比较 `lastUpdated`
- [ ] 本地反射更新后 import 更旧包 → 保留本地（新胜旧）
- [ ] 删除包: `/ai reflexpack delete iron_mining` → "删除成功"

---

## 5. 条件反射

### 5.1 学习

- [ ] 玩家执行动作（如挖矿）→ ObservedSequence 记录
- [ ] 多次重复后 → ConditionedReflex 在学习中
- [ ] Bayesian 收敛且 posterior > 0.5 → 状态从 `trial` → `healthy`

### 5.2 执行

- [ ] 条件满足时自动触发（P3 自动扫描: `scanAndTrigger()`）
- [ ] 命中后 atomIdx 逐步推进，atomProficiency +0.05/次
- [ ] 原子执行: `dispatchAction()` 正确映射到 BasicActionAdapter
- [ ] 未知 action（如拼写错误）→ 静默成功，无实际效果

### 5.3 衰减

- [ ] 执行失败 → stw 以 alpha = 0.1~0.6 衰减
- [ ] ltb 以 beta = 0.002~0.03 慢速跟踪 stw
- [ ] 置信度 = stw × 0.7 + ltb × 0.3

### 5.4 状态转换

- [ ] posterior ≤ 0.2，收敛 → 原子标记 `"impossible"`（永久跳过）
- [ ] posterior ≤ 0.2，收敛，无原子 → 反射标记 `"dormant"` → 归档到 `archived/`
- [ ] ⚠️ `"impossible"` 原子在 JSON 文件中的 `currentAtomIndex` 是否被跳过而不阻塞整个反射
- [ ] ⚠️ `"watching"` 状态 → 可恢复或继续退化，不直接 dormant
- [ ] ⚠️ atomIdx 跑完后不重置 → 反射卡在末端，始终走通用 execute 路径

### 5.5 溢出强化（Spillover）

- [ ] 同类别（如 `dig_ore`）多个反射 → 一个成功，同类 +30% delta
- [ ] 不同类别之间不互相影响

### 5.6 Precondition Guard

- [ ] 前置条件 `type:"item", key:"main_hand", match:"pickaxe"` → 空手时跳过
- [ ] `type:"state", key:"stress", operator:">", value:0.5` → 压力不够时跳过
- [ ] ⚠️ unknown precondition type → 静默通过（default true）
- [ ] ⚠️ fail_strategy=skip/wait/defer 三者无区别（MetaScheduler 均 break）

---

## 6. 反射包

- [ ] 导出含先验: `/ai reflexpack export botA pack1` → "含先验"
- [ ] 导出不含先验: `/ai reflexpack export botA pack1 noprior` → "不含先验"
- [ ] 非 OP 导入/删除 → "仅 OP 可导入/删除反射包"
- [ ] 导入不存在的包 → "包不存在"
- [ ] 删除不存在的包 → "包不存在"
- [ ] 空 bot（无反射）导出 → 可能失败
- [ ] 列表为空时 `/ai reflexpack list` → "没有已导入的反射包"

---

## 7. LLM 调用链

### 7.1 正常调用

- [ ] AI 模式已激活、有 LLM 冷却槽 → 正常调用 TemplateManager.fill()
- [ ] 返回值被正确解析为 JSON → 后续执行

### 7.2 Rate Limiting / Cooldown

- [ ] 两次指令间隔 < 20s → `LLM_COOLDOWN_TICKS(400)` 阻止第二次
- [ ] TemplateManager 全局 5s 冷却 → 连续快速调用被限
- [ ] ⚠️ DeepSeekClient 2s `Thread.sleep` 竞态 → 高并发下可能双穿

### 7.3 费用不足

- [ ] 使用余额为 0 的 key → `/ai setkey` 返回 "连接失败"
- [ ] 运行时余额耗尽 → Bot 静默停止响应（`AIResponse.empty()` 无提示）
- [ ] 余额耗尽后 `/ai apikey` → 仍显示 "AI模式已激活"
- [ ] 余额耗尽后 `/ai status` → 任务卡在 "执行中" 不推进

### 7.4 API 错误码

- [ ] 401 (Unauthorized) → `AIResponse.empty()` 静默返回
- [ ] 429 (Rate Limit) → 同上，❌ 无 `Retry-After` 处理
- [ ] 500 (Internal Error) → 同上
- [ ] 网络超时 (60s) → 同上，Exception
- [ ] DNS 解析失败 → Exception → `recentLLMFailure=true`

### 7.5 recentLLMFailure 永久锁

- [ ] API 调用失败 → `recentLLMFailure=true`, bot 不再调 LLM
- [ ] 此后任何指令 → bot 不响应
- [ ] 重启服务端 → `recentLLMFailure` 重置 → bot 恢复正常
- [ ] ❌ `recentLLMFailure` 永不自动重置，无 retry 逻辑

### 7.6 LLM 输出异常

- [ ] LLM 返回空 JSON `{}` → 静默丢弃
- [ ] LLM 返回纯文本（非 JSON）→ `JsonSyntaxException` → 静默丢弃
- [ ] LLM 返回 `IllegalStateException`（字段类型错）→ 未 catch，async 链崩溃

### 7.7 Chat Budget

- [ ] ChatBudget 耗尽 → 回退到 `LocalChatHandler` 本地处理
- [ ] Budget 重置后 → 恢复 LLM 调用

---

## 8. 安全系统

### 8.1 生命危急

- [ ] 生命 < 2 + 怪物 3 格内 → `critical` 反射触发 → flee
- [ ] 生命 < 10 + 怪物 10 格内 → `flee` 反射触发
- [ ] 安全反射被 InhibitoryControl 否决（barrier 阻挡/无敌对）→ 跳出不执行

### 8.2 环境威胁

- [ ] 饥饿 < 6 → `eat` 触发
- [ ] 熔岩 3 格内 → `avoid_lava` 触发
- [ ] 夜晚 → `seek_shelter` 触发
- [ ] 物品 5 格内 → `collect_item`
- [ ] 怪物 12 格内 → `sneak`
- [ ] 玩家 30 格内聊天 → `vocal_response`（invokeLLM）

### 8.3 E-Stop 安全否决链

- [ ] P0 安全反射 fired + critical=true → 跳过 P1-P5，直接返回
- [ ] P0.5 pre-frontal veto（InhibitoryControl）→ 否决后继续 P1-P5
- [ ] ⚠️ 多层安全同时触发（熔岩 + 低生命 + 怪物）→ 最高优先级的 `critical` 优先

---

## 9. 前额叶否决

### 9.1 Veto Safety

- [ ] 无敌对生物时 → flee 被 veto ("附近无敌对生物")
- [ ] 敌对太远 (>10 blocks) → veto
- [ ] 血量 > 15 + 弱敌 (僵尸/骷髅/蜘蛛) → veto
- [ ] 实体屏障阻挡 → veto

### 9.2 Veto Imitation

- [ ] moveTo 路径上 3×3 格内有熔岩 → veto
- [ ] jump 高度 > 5 格（坠落伤害）→ veto
- [ ] attack 目标是村民 → veto
- [ ] attack 目标是僵尸 → 通过

---

## 10. 激素系统

### 10.1 基础

- [ ] 初始 stress=0.1, curiosity=0.3, aggression=0.2
- [ ] 受击: stress +0.1
- [ ] 战斗胜利: aggression +0.05, stress -0.02
- [ ] 战斗失败: stress +0.15, aggression -0.1
- [ ] 新奇发现: curiosity +0.3 (cap 0.95)

### 10.2 边界

- [ ] ⚠️ 高压力 (stress > 0.5) → curiosity threshold × 1.5 → max curiosity(0.95) 可能被 clamp 到 1.0 而永远无法超过
- [ ] ⚠️ stress 持续 > 0.6 → candidate 中加入 "flee"，可能打断正常任务
- [ ] ⚠️ intimacy 不衰减 → 长时间后永久化
- [ ] stress < 0.3 + aggression < 0.4 → "routine" 模式

---

## 11. 贝叶斯系统

### 11.1 三层存储

- [ ] L1 shared_prior → 跨 bot 共享
- [ ] L2 per-bot posterior → 读取 `bots/{uuid}/bayesian/posterior.json`
- [ ] L3 anchoring context → 当前任务上下文过滤

### 11.2 可控性

- [ ] variance floor 0.1 → controllability 永远 ≤ 0.5 ❌
- [ ] `environment_change=true` → controllability / 2
- [ ] 非收敛反射 → controllability = 0.5（中性）

### 11.3 收敛

- [ ] sampleCount ≥ 5 + changeRate < 0.3679 → 收敛
- [ ] ⚠️ changeRate 要求极严 (|dPrior| < 0.0368) → 需要大量一致更新

### 11.4 方向

- [ ] 无经验时 `getCurrentDirection()` → "暂无经验数据"
- [ ] 所有 prior ≤ 0.5 → "暂无高置信度反射"
- [ ] 有高置信度反射 → top 5 排序返回

---

## 12. 记忆系统

### 12.1 MemoryManager

- [ ] 60s 缓存刷新 → 编辑文件后等待刷新
- [ ] ⚠️ 损坏 .mem 文件 → 静默跳过（无日志，无提示）
- [ ] ⚠️ `latest.mem` 被 filter 排除
- [ ] ❌ null MemoryEntry 被加入 memoryCache → 下游 NPE 风险
- [ ] ❌ 无同步锁 → 并发读写 CME 风险

### 12.2 MemoryGraph

- [ ] 节点 CRUD: 创建内存节点 → 持久化
- [ ] 边推断: TEMPORAL / CAUSAL / SIMILARITY / CONTRAST
- [ ] 图遍历 BFS → 不因循环而死循环（visited set）
- [ ] ⚠️ 自环 (A→A) 允许 → 不影响遍历
- [ ] ⚠️ 重复边 (A→B, A→B) 允许 → findEdge 只返回第一个
- [ ] Hebbian 强化: `reinforcePath(nodeIds, delta)` → 边权重更新
- [ ] 扩散激活: `traverse(maxDepth, minWeight)` → 三路候选合并

---

## 13. 多 Bot

- [ ] spawn bot2 → 自动复制 bot1 的反射（`copyReflexesFromMentor`）
- [ ] 检查复制的反射全部为 `trial` 状态
- [ ] 体重置为 0.5/0.5, proficiency=0.3, atomProficiency=0.1
- [ ] `/ai personality bot2` → 独立 α/β（随机初始化）
- [ ] despawn bot1 → 基因组存档到 `bots/genomes/`
- [ ] respawn → 从存档继承（GenomeArchivist）
- [ ] ⚠️ 三规则继承: 平均 + 减半 + 突变 → 验证参数分布
- [ ] ⚠️ 无 mentor 时 spawn → 干净状态

---

## 14. 降级与健壮

### 14.1 API 降级

- [ ] `/ai setkey clear` → 规则引擎模式 → 本地处理全部指令
- [ ] 规则引擎下所有功能可用（挖/打/建等原子动作）
- [ ] 运行时网络断开 → 不崩溃，Bot 停止 LLM 响应
- [ ] 网络恢复后需重启服务端才能重新调 LLM（`recentLLMFailure` 锁）
- [ ] 重启后自动恢复 AI 模式

### 14.2 文件健壮

- [ ] 删除整个 `run/ai_memory/` → 系统自动重建
- [ ] 删除 `conditioned/` 目录 → ReflexPackManager 导出返回 false
- [ ] 写入损坏 JSON 到 `api_key.json` → AIConfig.load() 回到默认
- [ ] 并发文件写入（多个操作同时 save）→ 原子写 `.tmp`+rename 防损坏

### 14.3 Bot 健壮

- [ ] bot 在 tick 中被移除 → `tickAll()` 自动清理
- [ ] `TemporalScaler.getSpeed() < 0.01f` → bot 跳过 tick 处理
- [ ] BotManager 为 null 时所有命令 → "Bot系统未初始化"

### 14.4 内存安全

- [ ] ❌ `memoryCache` 无锁 → 多线程 iterate+add 可能 CME
- [ ] ❌ `ConditionedReflex.consecutiveFailures` 全局共享 → reflex A 的失败影响 reflex B
- [ ] ❌ 空 `MemoryEntry` 数组元素→下游 `.timestamp` 时 NPE

---

## 15. 模糊边界综合

### 15.1 LLM 并发

- [ ] 快速连续 @两个不同 bot → 可能穿透 2s rate limit（竞态）
- [ ] ❌ 同时发 3+ 条 → Thread.sleep 竞态，多线程同时发送
- [ ] LLM 调用中服务器关闭 → 优雅处理

### 15.2 聊天窗口

- [ ] 连续发 >6 条消息 → 窗口滑动，最旧消息被丢弃
- [ ] ⚠️ 6 条玩家消息 + 1 条 bot 自动消息 → 可能丢玩家消息留 bot 消息

### 15.3 跨会话

- [ ] bot despawn → respawn（新 UUID）→ 记忆/反射文件仍从旧 UUID 目录残留
- [ ] ⚠️ 新 bot 无 mentor 时旧反射文件不会被加载
- [ ] `genomes/` 目录积累 → 多次 spawn/despawn 后存档无限增长

### 15.4 Persona 一致性

- [ ] Persona 仅在 TemplateManager 流程中被注入
- [ ] ❌ AIChatHandler/AITaskPlanner 独有流程不被 Persona 影响

### 15.5 E-Stop + 任务混合

- [ ] bot 执行任务中健康 < 2 + 怪物靠近 → 安全反射打断任务
- [ ] 安全状态解除后 → 任务恢复或报告中断
- [ ] ⚠️ 任务状态为 "执行中" 但 bot 已停止 → `/ai status` 显示矛盾

---

## 16. 性能与稳定性

- [ ] 连续 tick 5 分钟无人工干预 → 无异常日志
- [ ] 同时 spawn 5 个 bot → 无明显卡顿
- [ ] 大量记忆文件（50+ .mem）→ MemoryManager refresh 不挂
- [ ] 执行日志不断增长 → 无 OOM

---

## 汇总

| 大类 | 场景数 | 通过 | 失败 | 阻塞 |
|:----:|:-----:|:----:|:----:|:----:|
| 1. 基础接入 | 8 | | | |
| 2. 假人管理 | 12 | | | |
| 3. 命令路由 | 10 | | | |
| 4. 自然语言 | 13 | | | |
| **B1. 层间拦截** | **8** | | | |
| **B2. 记忆vs技能** | **4** | | | |
| **B3. 统计vs决策** | **4** | | | |
| **B4. DAG↔Chain** | **6** | | | |
| **B5. 模板填空** | **7** | | | |
| **B6. 反射包移植** | **7** | | | |
| 5. 条件反射 | 18 | | | |
| 6. 反射包操作 | 7 | | | |
| 7. LLM 调用链 | 14 | | | |
| 8. 安全系统 | 9 | | | |
| 9. 前额叶否决 | 7 | | | |
| 10. 激素系统 | 8 | | | |
| 11. 贝叶斯 | 8 | | | |
| 12. 记忆系统 | 9 | | | |
| 13. 多Bot | 8 | | | |
| 14. 降级与健壮 | 10 | | | |
| 15. 模糊边界 | 10 | | | |
| 16. 性能稳定性 | 4 | | | |
| **合计** | **~140** | — | — | — |
