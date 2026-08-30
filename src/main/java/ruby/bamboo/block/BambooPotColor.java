package ruby.bamboo.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;

/**
 * 竹鉢の色 — SakuraLeave 8色を核に、欠落8色をバニラ調で補完。既定はBROWN。
 * WHITE/PURPLE/MAGENTA/PINK/GREEN/RED/YELLOW/ORANGE は SakuraLeave.EnumLeave の色を流用。
 */
public enum BambooPotColor implements StringRepresentable {
    WHITE(DyeColor.WHITE, 0xFFFFFF),
    ORANGE(DyeColor.ORANGE, 0xFFC600),
    MAGENTA(DyeColor.MAGENTA, 0xF09090),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 0x3AB3DA),
    YELLOW(DyeColor.YELLOW, 0xF5E600),
    LIME(DyeColor.LIME, 0x80C71F),
    PINK(DyeColor.PINK, 0xFFC5CC),
    GRAY(DyeColor.GRAY, 0x474F52),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 0x9D9D97),
    CYAN(DyeColor.CYAN, 0x169C9C),
    PURPLE(DyeColor.PURPLE, 0xFFC5FC),
    BLUE(DyeColor.BLUE, 0x3C44AA),
    BROWN(DyeColor.BROWN, 0x835432),
    GREEN(DyeColor.GREEN, 0x3F9E55),
    RED(DyeColor.RED, 0xC80010),
    BLACK(DyeColor.BLACK, 0x1D1D21);

    @Nullable
    public final DyeColor dye;
    public final int color;

    BambooPotColor(@Nullable DyeColor dye, int color) {
        this.dye = dye;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    @Nullable
    public static BambooPotColor fromDye(DyeColor dye) {
        for (BambooPotColor c : values()) if (c.dye == dye) return c;
        return null;
    }

    public static BambooPotColor fromDyeOrDefault(DyeColor dye) {
        BambooPotColor c = fromDye(dye);
        return c != null ? c : BROWN;
    }
}
