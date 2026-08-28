package ruby.bamboo.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;

/**
 * 温泉色 — 源泉 SpringBlock が保持。温泉水は PARENT_DIR で源泉を辿ってこの色を参照する。
 * Forest既定 0x3F76E4 はバニラ Forest水と同色。
 */
public enum SpringColor implements StringRepresentable {
    DEFAULT(null, 0x3F76E4),
    VANILLA(null, 0x3F76E4),
    WHITE(DyeColor.WHITE, 0xF0F0F0),
    ORANGE(DyeColor.ORANGE, 0xF9801D),
    MAGENTA(DyeColor.MAGENTA, 0xC74EBD),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 0x3AB3DA),
    YELLOW(DyeColor.YELLOW, 0xFED83D),
    LIME(DyeColor.LIME, 0x80C71F),
    PINK(DyeColor.PINK, 0xF38BAA),
    GRAY(DyeColor.GRAY, 0x474F52),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 0x9D9D97),
    CYAN(DyeColor.CYAN, 0x169C9C),
    PURPLE(DyeColor.PURPLE, 0x8932B8),
    BLUE(DyeColor.BLUE, 0x3C44AA),
    BROWN(DyeColor.BROWN, 0x835432),
    GREEN(DyeColor.GREEN, 0x5E7C16),
    RED(DyeColor.RED, 0xB02E26),
    BLACK(DyeColor.BLACK, 0x1D1D21);

    @Nullable
    public final DyeColor dye;
    public final int color; // 0xRRGGBB

    SpringColor(@Nullable DyeColor dye, int color) {
        this.dye = dye;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    @Nullable
    public static SpringColor fromDye(DyeColor dye) {
        for (SpringColor c : values()) {
            if (c.dye == dye) return c;
        }
        return null;
    }

    public static SpringColor fromDyeOrDefault(DyeColor dye) {
        SpringColor c = fromDye(dye);
        return c != null ? c : DEFAULT;
    }
}
