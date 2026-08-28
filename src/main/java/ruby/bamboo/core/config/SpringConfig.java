package ruby.bamboo.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 温泉（源泉+温泉水）用 Config — bamboomod-spring.toml (COMMON)。
 * <p>
 * 2026-08-27確定パラメータ。
 */
public class SpringConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    public static class Common {
        public final ForgeConfigSpec.IntValue maxSpreadRadius;
        public final ForgeConfigSpec.IntValue sourceTickDelay;
        public final ForgeConfigSpec.IntValue waterTickDelay;
        public final ForgeConfigSpec.IntValue evaporationDelay;
        public final ForgeConfigSpec.IntValue maxLevel;
        public final ForgeConfigSpec.IntValue reparentInterval;
        public final ForgeConfigSpec.IntValue tintColor;

        public Common(ForgeConfigSpec.Builder b) {
            b.push("spring");
            maxSpreadRadius = b.comment("Max horizontal spread radius (manhattan XZ) from parent. 0 = unlimited. Default 32 = 2 chunks")
                    .defineInRange("maxSpreadRadius", 32, 0, 64);
            sourceTickDelay = b.comment("SpringBlock scheduled tick delay (ticks). Old tickRate 20")
                    .defineInRange("sourceTickDelay", 20, 1, 100);
            waterTickDelay = b.comment("SpringWater levelUp / scheduledTick delay (ticks). Old 30 -> 20")
                    .defineInRange("waterTickDelay", 20, 1, 100);
            evaporationDelay = b.comment("Drying levelDown delay (ticks) after source OFF")
                    .defineInRange("evaporationDelay", 30, 1, 100);
            maxLevel = b.comment("Max spring level (1..8) — LEVEL 0=満水(高) 7=低水位 に対応")
                    .defineInRange("maxLevel", 8, 1, 8);
            reparentInterval = b.comment("Reparent to nearest parent interval (ticks)")
                    .defineInRange("reparentInterval", 5, 1, 20);
            tintColor = b.comment("Hot spring tint color (ARGB without alpha, e.g. 0xE0F8FF)")
                    .defineInRange("tintColor", 0xE0F8FF, 0, 0xFFFFFF);
            b.pop();
        }
    }
}
