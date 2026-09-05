package ruby.bamboo.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 色付き光 (間接照明) の昼光抑制設定。CLIENT専用。
 * <p>
 * 太陽光が強い日中は発色光を弱め、夜間は全量にする。判定は着色位置のスカイライト
 * (時刻・天候・屋内外が折り込み済みのバニラ算出値) を使うため、新規パケット不要。
 * bamboomod-coloredlight.toml に保存。
 */
public class ColoredLightConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CLIENT = new Client(b);
        CLIENT_SPEC = b.build();
    }

    public static class Client {
        /** 昼光抑制の有効/無効 */
        public final ForgeConfigSpec.BooleanValue daylightSuppressEnabled;
        /** 日中屋外での抑制率 (0.0-1.0)。alpha *= 1-daylight*rate */
        public final ForgeConfigSpec.DoubleValue daylightSuppressRate;
        /** 昼光の量子化レベル数。レベル変化時のみ再焼き込み (日中・夜間は不変で無コスト) */
        public final ForgeConfigSpec.IntValue daylightLevels;        /** 1tickにdirty化する最大セクション数 (負荷分散用) */
        public final ForgeConfigSpec.IntValue sectionsPerTick;
        /** 光源からdirty化するセクション半径 (getTintの半径12に合わせる) */
        public final ForgeConfigSpec.IntValue dirtyRadius;

        public Client(ForgeConfigSpec.Builder b) {
            b.push("coloredlight");
            b.push("daylight");
            daylightSuppressEnabled = b
                    .comment("Weaken colored light where vanilla skylight is strong (daytime outdoors). Indoor/night stays full.")
                    .define("suppressEnabled", true);
            daylightSuppressRate = b
                    .comment("Suppression strength at full daylight (0.0-1.0). 0.75 leaves 0.25 at noon.")
                    .defineInRange("suppressRate", 0.75, 0.0, 1.0);
            daylightLevels = b
                    .comment("Quantized daylight levels. Re-bake near lights only when the level changes (flat day/night = free).")
                    .defineInRange("levels", 8, 2, 32);
            sectionsPerTick = b
                    .comment("Max sections marked dirty per tick (near-first, far chunks delayed)")
                    .defineInRange("sectionsPerTick", 16, 1, 64);
            dirtyRadius = b
                    .comment("Section-dirty radius around each light in blocks (match getTint radius)")
                    .defineInRange("dirtyRadius", 12, 4, 24);
            b.pop();
            b.pop();
        }
    }
}
