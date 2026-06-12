package com.izimi.eagent.cortex.api;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.cortex.api.TemplateManager.TemplateType;
import com.izimi.eagent.cortex.chat.LocalChatHandler;

import java.util.List;
import java.util.regex.Pattern;

public class TemplateMatcher {

    private static final List<Pattern> CLARIFICATION_PATTERNS = List.of(
        Pattern.compile("(.?怎么|如何|怎样|能不能|可以.?(吗|么)|说明|解释)(?!办)(?!样)(?!回事)"),
        Pattern.compile("(我想|我要|帮我|给我|能不能)"),
        Pattern.compile("^(你|你帮我).{0,3}(做|弄|搞|建|造|打|挖|种|喂)"),
        Pattern.compile("[?？]$"),
        Pattern.compile("^(调|改|变成|设置为|改成)")
    );

    private static final List<Pattern> TASK_PATTERNS = List.of(
        Pattern.compile("^(挖|打|建|造|种|收集|找|去|杀|做|生产|获取|采|制作|建造|盖|放置|合成|拿)(.{0,10}(个|的|些|点)?)"),
        Pattern.compile("\\d+个")
    );

    private static final List<Pattern> REFLEX_PATTERNS = List.of(
        Pattern.compile("(学|教|记住|反射|习惯|自动)(.{0,5}(挖|打|建|种|做|合成))"),
        Pattern.compile("(如果|当|遇到).*(就|则|可以|应该)"),
        Pattern.compile("(行为|反应|响应|动作|任务).*(规则|模式|方式)")
    );

    private static final List<Pattern> SOCIAL_PATTERNS = List.of(
        Pattern.compile("(你好|嗨|hi|hello|yo|早)"),
        Pattern.compile("(谢谢|多谢|thx|thanks)"),
        Pattern.compile("(再见|拜拜|bye|拜)"),
        Pattern.compile("(厉害|牛逼|真棒)"),
        Pattern.compile("(傻|笨|蠢)"),
        Pattern.compile("(喵|~)$"),
        Pattern.compile("^(在吗|在不在)")
    );

    public TemplateType match(String message, BotContext botCtx, WorldContext worldCtx) {
        if (message == null || message.isBlank()) return null;

        // 1. 本地聊天处理 (0 成本)
        LocalChatHandler chatHandler = worldCtx.cortex().chatHandler();
        if (chatHandler != null && chatHandler.canHandle(message)) return null;

        // 2. 条件反射扫描匹配 (0 成本) — 在 executeHabitLayer 中完成，这里不重复

        // 3. 模板分类
        String lower = message.toLowerCase();

        // 3a. 明确任务请求
        for (Pattern p : TASK_PATTERNS) {
            if (p.matcher(lower).find()) return TemplateType.TASK_PLAN;
        }

        // 3b. 反射学习请求
        for (Pattern p : REFLEX_PATTERNS) {
            if (p.matcher(lower).find()) return TemplateType.REFLEX_CREATE;
        }

        // 3c. 纯社交/闲聊
        for (Pattern p : SOCIAL_PATTERNS) {
            if (p.matcher(lower).find()) return TemplateType.CHAT_RESPONSE;
        }

        // 3d. 模糊输入 → CLARIFICATION
        for (Pattern p : CLARIFICATION_PATTERNS) {
            if (p.matcher(lower).find()) return TemplateType.CLARIFICATION;
        }

        // 4. 默认: 如果有具体名词(矿石、怪物等) → TASK_PLAN, 否则 CHAT_RESPONSE
        if (hasConcreteNoun(lower)) return TemplateType.TASK_PLAN;
        return TemplateType.CHAT_RESPONSE;
    }

    private boolean hasConcreteNoun(String lower) {
        String[] concreteNouns = {"矿", "石", "铁", "金", "钻", "木", "石", "剑", "镐", "斧",
            "锹", "锄", "弓", "箭", "床", "箱", "炉", "门", "栅", "药", "食", "肉",
            "草", "花", "树", "怪", "猪", "牛", "羊", "鸡", "鱼", "龙", "人", "家",
            "火", "水", "地", "天", "空", "房", "桌", "灯", "梯", "墙"};
        for (String noun : concreteNouns) {
            if (lower.contains(noun)) return true;
        }
        return false;
    }
}
