package ruby.bamboo.gacha;

import net.minecraft.util.RandomSource;

/**
 * ガチャカプセルの色 (NBT {@code Capsule} で保持・単一アイテムで色分け)。
 * <p>
 * マシン排出率: 赤60 / 青30 / 黄9 / 虹1。右に行くほどレア。
 * カプセル色ごと finales 中身のレアリティ期待値が変わる。
 */
public enum GachaCapsule {
    /** 赤: 排出60%。中身 C90 / R9 / SR1 */
    RED("red", 60, 0xE23B3B, 90, 9, 1),
    /** 青: 排出30%。中身 C70 / R27 / SR3 */
    BLUE("blue", 30, 0x3F8FF5, 70, 27, 3),
    /** 黄: 排出9%。中身 C50 / R45 / SR5 */
    YELLOW("yellow", 9, 0xF2C41E, 50, 45, 5),
    /** 虹: 排出1%。中身 C30 / R60 / SR10。tintは時刻で循環 */
    RAINBOW("rainbow", 1, -1, 30, 60, 10);

    /** NBT値 */
    public final String id;
    /** マシン排出の重み */
    public final int machineWeight;
    /** 上半分のtint色 (RGB)。RAINBOWは-1=動的 */
    public final int tint;
    /** 中身レアリティの重み */
    public final int wCommon;
    /** 中身レアリティの重み */
    public final int wRare;
    /** 中身レアリティの重み */
    public final int wSuperRare;

    GachaCapsule(String id, int machineWeight, int tint, int wCommon, int wRare,
            int wSuperRare) {
        this.id = id;
        this.machineWeight = machineWeight;
        this.tint = tint;
        this.wCommon = wCommon;
        this.wRare = wRare;
        this.wSuperRare = wSuperRare;
    }

    /** 表示・ツールチップ用langキー (名前は「カプセル」で統一、本キーは色名のみ)。 */
    public String langKey() {
        return "tooltip.bamboomod.gacha_capsule." + this.id;
    }

    /** ワンランク上のカプセル (当たり用。虹は虹のまま)。 */
    public GachaCapsule higher() {
        return switch (this) {
            case RED -> BLUE;
            case BLUE -> YELLOW;
            case YELLOW -> RAINBOW;
            default -> RAINBOW;
        };
    }

    public static GachaCapsule byId(String id) {
        if (id != null) {
            for (GachaCapsule c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
        }
        return RED;
    }

    /** マシン排出抽選 (60/30/9/1)。 */
    public static GachaCapsule rollCapsule(RandomSource random) {
        int total = 0;
        for (GachaCapsule c : values()) {
            total += c.machineWeight;
        }
        int t = random.nextInt(total);
        for (GachaCapsule c : values()) {
            t -= c.machineWeight;
            if (t < 0) {
                return c;
            }
        }
        return RED;
    }

    /** カプセル色に応じた中身レアリティ抽選。 */
    public GachaRarity rollContent(RandomSource random) {
        int total = this.wCommon + this.wRare + this.wSuperRare;
        int t = random.nextInt(total);
        if (t < this.wSuperRare) {
            return GachaRarity.SUPER_RARE;
        }
        if (t < this.wSuperRare + this.wRare) {
            return GachaRarity.RARE;
        }
        return GachaRarity.COMMON;
    }
}
