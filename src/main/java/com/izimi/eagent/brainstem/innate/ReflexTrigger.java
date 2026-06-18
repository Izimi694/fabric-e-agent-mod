package com.izimi.eagent.brainstem.innate;

public record ReflexTrigger(TriggerType type, double value, int range) {

    public enum TriggerType {
        HEALTH_BELOW,
        HUNGER_BELOW,
        MONSTER_NEARBY,
        LAVA_NEARBY,
        TIME_OF_DAY,
        ITEM_NEARBY,
        CHAT_PRESENCE,
        ARMOR_SLOT_EMPTY,
        OFFHAND_EMPTY,
        HAS_TOTEM,
        BOW_IN_HOTBAR,
        ARROW_IN_INVENTORY
    }
}
