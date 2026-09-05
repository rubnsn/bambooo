package ruby.bamboo.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 竹の願い用 Config — bamboomod-wish.toml (COMMON)。
 * <p>
 * port-spec-wish §5 準拠。
 */
public class WishConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    public static class Common {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue chance;
        public final ModConfigSpec.IntValue cooldownTicks;
        public final ModConfigSpec.BooleanValue allowCreative;
        public final ModConfigSpec.ConfigValue<String> fallbackMode;
        public final ModConfigSpec.IntValue wishTimeoutTicks;
        public final ModConfigSpec.BooleanValue punishGreed;
        public final ModConfigSpec.IntValue overenchantChance;

        public Common(ModConfigSpec.Builder b) {
            b.push("wish");
            enabled = b.comment("Enable wish feature")
                    .define("enabled", true);
            chance = b.comment("Trigger chance 1/chance per bamboo break with axe")
                    .defineInRange("chance", 2048, 1, 1_000_000);
            cooldownTicks = b.comment("Cooldown ticks per player")
                    .defineInRange("cooldownTicks", 6000, 0, 72000);
            allowCreative = b.comment("Allow creative mode to trigger")
                    .define("allowCreative", true);
            fallbackMode = b.comment("Fallback mode: random / nothing / iron")
                    .define("fallbackMode", "random", o -> o instanceof String s
                            && (s.equalsIgnoreCase("random") || s.equalsIgnoreCase("nothing") || s.equalsIgnoreCase("iron")));
            wishTimeoutTicks = b.comment("Pending timeout ticks for wish request")
                    .defineInRange("wishTimeoutTicks", 400, 100, 6000);
            punishGreed = b.comment("Enable greed punishment")
                    .define("punishGreed", true);
            overenchantChance = b.comment("Chance 1/overenchantChance that one enchantment on a wished tool exceeds its max level by 1 (0 to disable)")
                    .defineInRange("overenchantChance", 10, 0, 1000);
            b.pop();
        }
    }
}
