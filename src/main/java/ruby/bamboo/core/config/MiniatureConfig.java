package ruby.bamboo.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ミニチュア用の Config — パーティクル数など独自ルールを定義。
 * <p>
 * QA要望: 周囲のミニチュア内部で発生可能なパーティクル数を独自に制限したい。
 * Client側で有効。ModConfigSpec で定義し、bamboomod-miniature.toml に保存される。
 */
public class MiniatureConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CLIENT = new Client(b);
        CLIENT_SPEC = b.build();
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue particleEnabled;
        public final ModConfigSpec.IntValue particlesPerMiniaturePerTick;
        public final ModConfigSpec.IntValue maxParticlesPerClientTick;
        public final ModConfigSpec.DoubleValue particleSpawnChance;
        public final ModConfigSpec.DoubleValue particleDistance;
        public final ModConfigSpec.IntValue particleTickInterval;
        // Render budget (face culling + internal cell limit)
        public final ModConfigSpec.IntValue maxCellsPerFrame;
        public final ModConfigSpec.DoubleValue maxRenderDistance;
        public final ModConfigSpec.EnumValue<PlaceholderMode> placeholderMode;
        public final ModConfigSpec.BooleanValue lodBoundaryShell;
        public final ModConfigSpec.IntValue sortInterval;

        public Client(ModConfigSpec.Builder b) {
            b.push("miniature");
            b.push("particle");
            particleEnabled = b
                    .comment("Enable scaled particle spawning inside miniature (client only)")
                    .define("enabled", true);
            particlesPerMiniaturePerTick = b
                    .comment("Max particles spawned per miniature BE per tick (0-10)")
                    .defineInRange("perMiniaturePerTick", 2, 0, 10);
            maxParticlesPerClientTick = b
                    .comment("Global max particles per client tick from all nearby miniatures (0-512)")
                    .defineInRange("globalMaxPerTick", 64, 0, 512);
            particleSpawnChance = b
                    .comment("Chance per candidate cell to actually spawn (0.0-1.0). Torch and campfire always try.")
                    .defineInRange("spawnChance", 0.3, 0.0, 1.0);
            particleDistance = b
                    .comment("Max distance from player to spawn particles (blocks)")
                    .defineInRange("distance", 32.0, 4.0, 128.0);
            particleTickInterval = b
                    .comment("Spawn attempt every N ticks (1=every tick)")
                    .defineInRange("tickInterval", 2, 1, 20);
            b.pop();
            b.push("render");
            maxCellsPerFrame = b
                    .comment("Max non-air internal cells rendered per frame (0=unlimited). Low value keeps FPS stable (512 default).")
                    .defineInRange("maxCellsPerFrame", 512, 0, 65536);
            maxRenderDistance = b
                    .comment("Max distance from camera to render miniature detail (blocks). Beyond this only placeholder is drawn.")
                    .defineInRange("maxRenderDistance", 48.0, 4.0, 128.0);
            placeholderMode = b
                    .comment("How to render miniatures over budget/distance: WIREFRAME=wire box, TRANSLUCENT=semi-transparent cube, HIDDEN=skip")
                    .defineEnum("placeholderMode", PlaceholderMode.WIREFRAME);
            lodBoundaryShell = b
                    .comment("If true, the first miniature over budget renders as shell-only (boundary cells) for smooth transition (B plan)")
                    .define("lodBoundaryShell", true);
            sortInterval = b
                    .comment("Sort interval for render budget (ticks). Lower is more responsive but costs more.")
                    .defineInRange("sortInterval", 5, 1, 20);
            b.pop();
            b.pop();
        }
    }

    public enum PlaceholderMode {
        WIREFRAME,
        TRANSLUCENT,
        HIDDEN
    }
}
