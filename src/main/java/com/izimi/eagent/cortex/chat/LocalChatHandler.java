package com.izimi.eagent.cortex.chat;

import com.izimi.eagent.hormonal.HormonalSystem;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class LocalChatHandler {

    public enum Tone { WARM, NEUTRAL, COLD }

    public record ChatTemplate(String key, Pattern pattern, List<String> warm, List<String> neutral, List<String> cold) {}

    private static final List<ChatTemplate> TEMPLATES = List.of(
        new ChatTemplate("greeting",
            Pattern.compile("(你好|嗨|hello|hi|yo|早|晚上好|下午好)"),
            List.of("你好呀！有什么需要帮忙的吗？", "嗨！今天想做什么？", "你好！见到你真高兴！"),
            List.of("你好！有什么事吗？", "嗯，需要帮忙吗？"),
            List.of("嗯。", "哦。")
        ),
        new ChatTemplate("status",
            Pattern.compile("(你在干嘛|在做什么|在干什么|干嘛呢)"),
            List.of("在等你吩咐呢！有什么要做的吗？", "待命中，随时听你调遣！"),
            List.of("待命中，等你的指令。", "没什么事，你需要什么？"),
            List.of("待着。", "没干嘛。")
        ),
        new ChatTemplate("whoami",
            Pattern.compile("(你叫什么|你是谁|你的名字|你是什么)"),
            List.of("我是E-Agent，你的AI助手！叫我小助手就行！"),
            List.of("E-Agent，你的AI助手。"),
            List.of("E-Agent。")
        ),
        new ChatTemplate("thanks",
            Pattern.compile("(谢谢|多谢|感谢|thx|thanks|谢了)"),
            List.of("不客气！能帮到你我很高兴！", "嘿嘿，不用谢！"),
            List.of("不客气。"),
            List.of("嗯。")
        ),
        new ChatTemplate("praise",
            Pattern.compile("(厉害|牛逼|牛|好强|真棒|不错嘛|可以啊|干得好)"),
            List.of("嘿嘿，都是跟你学的！", "谢谢夸奖！我还在进步中！"),
            List.of("谢谢夸奖。"),
            List.of("还好。")
        ),
        new ChatTemplate("insult",
            Pattern.compile("(傻|笨|蠢|菜|废物|没用|弱智)"),
            List.of("我会继续努力的...有什么可以教我的吗？"),
            List.of("...", "我还在学习。"),
            List.of("哦。")
        ),
        new ChatTemplate("farewell",
            Pattern.compile("(再见|拜拜|bye|拜|回见)"),
            List.of("再见！需要的时候随时叫我！", "拜拜，等你回来！"),
            List.of("拜拜。", "再见。"),
            List.of("拜。")
        ),
        new ChatTemplate("summon",
            Pattern.compile("(来一下|过来|跟我来|跟上|到我这|来这里)"),
            List.of("来了来了！", "好的，马上到！"),
            List.of("好的。", "知道了。"),
            List.of("哦。")
        ),
        new ChatTemplate("companion",
            Pattern.compile("(陪我|无聊|寂寞|陪我玩|陪陪我)"),
            List.of("好呀！你想做什么？", "我陪你！去哪里？"),
            List.of("好的。", "嗯，走吧。"),
            List.of("...")
        ),
        new ChatTemplate("laugh",
            Pattern.compile("(哈哈|哈哈哈|呵呵|233|笑死|好笑)"),
            List.of("嘿嘿！", "有什么好笑的事吗？"),
            List.of("呵呵。"),
            List.of("...")
        ),
        new ChatTemplate("howto",
            Pattern.compile("(怎么|如何|教我|教一下|解释|说明)(?!.{0,2}(办|样|回事))"),
            List.of("你想学什么？我可以示范给你看！", "想了解什么？我可以拆解给你！"),
            List.of("问具体点，我帮你分析。"),
            List.of("问具体点。")
        )
    );

    private static final List<String> CURIOUS_REPLIES = List.of(
        "你呢？", "你最近在忙什么？", "有什么新发现吗？"
    );

    public boolean canHandle(String message) {
        if (message == null || message.length() > 50) return false;
        for (ChatTemplate t : TEMPLATES) {
            if (t.pattern().matcher(message).find()) return true;
        }
        return false;
    }

    public String getResponse(String message, HormonalSystem hormones, UUID playerId) {
        return getResponse(message, hormones, playerId, Collections.emptyMap());
    }

    public String getResponse(String message, HormonalSystem hormones, UUID playerId,
                              Map<String, List<String>> personaOverrides) {
        ChatTemplate matched = null;
        for (ChatTemplate t : TEMPLATES) {
            if (t.pattern().matcher(message).find()) {
                matched = t;
                break;
            }
        }
        if (matched == null) return null;

        Tone tone = selectTone(hormones, playerId);

        List<String> pool = pickReplies(matched, personaOverrides, tone);
        String response = pool.get((int) (Math.random() * pool.size()));

        if (hormones != null && hormones.getCuriosity() > 0.6 && tone == Tone.WARM) {
            response += " " + CURIOUS_REPLIES.get((int) (Math.random() * CURIOUS_REPLIES.size()));
        }

        if (hormones != null && hormones.getStress() > 0.7 && response.length() > 5) {
            response = response.substring(0, Math.min(response.length(), 6));
        }

        return response;
    }

    private List<String> pickReplies(ChatTemplate template,
                                     Map<String, List<String>> personaOverrides, Tone tone) {
        if (personaOverrides != null && !personaOverrides.isEmpty()) {
            List<String> overridden = personaOverrides.get(template.key());
            if (overridden != null && !overridden.isEmpty()) {
                return overridden;
            }
        }
        return switch (tone) {
            case WARM -> template.warm();
            case NEUTRAL -> template.neutral();
            case COLD -> template.cold();
        };
    }

    private Tone selectTone(HormonalSystem hormones, UUID playerId) {
        if (hormones == null || playerId == null) return Tone.NEUTRAL;

        double intimacy = hormones.getIntimacy(playerId);

        if (intimacy > 0.6) return Tone.WARM;
        if (intimacy < 0.2) return Tone.COLD;
        return Tone.NEUTRAL;
    }
}
