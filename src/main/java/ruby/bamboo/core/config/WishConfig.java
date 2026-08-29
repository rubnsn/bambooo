package ruby.bamboo.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 竹の願い用 Config — bamboomod-wish.toml (COMMON)。
 * <p>
 * port-spec-wish §5 準拠。
 */
public class WishConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.IntValue chance;
        public final ForgeConfigSpec.IntValue cooldownTicks;
        public final ForgeConfigSpec.BooleanValue allowCreative;
        public final ForgeConfigSpec.ConfigValue<String> fallbackMode;
        public final ForgeConfigSpec.IntValue wishTimeoutTicks;
        public final ForgeConfigSpec.BooleanValue punishGreed;

        public Common(ForgeConfigSpec.Builder b) {
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
            b.pop();
        }
    }
}
