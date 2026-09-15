package ruby.bamboo.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;

/**
 * 桜の葉の色。旧 SakuraLeave.EnumLeave (1.10.2) の移植。
 * <p>
 * 旧8色 (SAKURA系4 + BROAD系4) は旧hexを維持し、欠落8色は染料色
 * (旧 {@code DyeColor#getTextureDiffuseColors} 相当。1.21 で削除されたため
 * {@link BambooPotColor} と同一のバニラ調hexを直書き) から採用。全16染料対応。
 * 幹は桜固定・maple等への分岐は廃止。petal は全色1 (桜の花びらのみ)。
 */
public enum SakuraLeaveColor implements StringRepresentable {
    WHITE(DyeColor.WHITE, 0xFFFFFF, 1),
    ORANGE(DyeColor.ORANGE, 0xFFC600, 1),
    MAGENTA(DyeColor.MAGENTA, 0xF09090, 1),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 0x3AB3DA, 1),
    YELLOW(DyeColor.YELLOW, 0xF5E600, 1),
    LIME(DyeColor.LIME, 0x80C71F, 1),
    PINK(DyeColor.PINK, 0xFFC5CC, 1),
    GRAY(DyeColor.GRAY, 0x474F52, 1),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 0x9D9D97, 1),
    CYAN(DyeColor.CYAN, 0x169C9C, 1),
    PURPLE(DyeColor.PURPLE, 0xFFC5FC, 1),
    BLUE(DyeColor.BLUE, 0x3C44AA, 1),
    BROWN(DyeColor.BROWN, 0x835432, 1),
    GREEN(DyeColor.GREEN, 0x3F9E55, 1),
    RED(DyeColor.RED, 0xC80010, 1),
    BLACK(DyeColor.BLACK, 0x1D1D21, 1);

    public final DyeColor dye;
    /** 葉・花びらの表示色 */
    public final int color;
    /** 花びらテクスチャ番号。桜のみのため全色1 (PETAL_1) */
    public final int petal;

    SakuraLeaveColor(DyeColor dye, int color, int petal) {
        this.dye = dye;
        this.color = color;
        this.petal = petal;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    /** 全16染料対応のため非null */
    public static SakuraLeaveColor fromDye(DyeColor dye) {
        for (SakuraLeaveColor c : values()) {
            if (c.dye == dye) {
                return c;
            }
        }
        return PINK;
    }
}
