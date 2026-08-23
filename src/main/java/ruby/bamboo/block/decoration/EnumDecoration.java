package ruby.bamboo.block.decoration;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

/**
 * 無機能デコレーションブロックの種類。旧 EnumDecoration (1.10.2) の移植。
 * <p>
 * 各種類につき 通常ブロック / ハーフブロック / 階段 を自動生成する。
 */
public enum EnumDecoration implements StringRepresentable {
    KAWARA("kawara", 0xFF707070),
    PLASTER("plaster", 0xFFF0EFE4),
    NAMAKO("namako", 0xFF606060),
    WARA("wara", 0xFFD8B719),
    KAYA("kaya", 0xFF9C7F4E),
    CBIRCH("cbirch", 0xFFC5B77C),
    COAK("coak", 0xFFB8945F),
    CPINE("cpine", 0xFF7A5B33);

    public static final String SLAB = "_slab";
    public static final String DOUBLE_SLAB_SUFFIX = "_double_slab";
    public static final String STAIRS = "_stairs";

    private final String name;
    private final int mapColorRgb;

    EnumDecoration(String name, int mapColorRgb) {
        this.name = name;
        this.mapColorRgb = mapColorRgb;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** 通常ブロックの登録名 */
    public String getBlockName() {
        return this.name;
    }

    public String getSlabName() {
        return this.name + SLAB;
    }

    /** ダブルスラブは内部用(アイテムなし)。旧版との互換用に登録だけしておく */
    public String getDoubleSlabName() {
        return this.name + DOUBLE_SLAB_SUFFIX;
    }

    public String getStairsName() {
        return this.name + STAIRS;
    }

    /** MapColor用RGB (net.minecraft.world.level.material.MapColor への変換は呼び出し側で) */
    public int getMapColorRgb() {
        return this.mapColorRgb;
    }

    /**
     * 登録名から種類を逆引き (slab/stairs サフィックスを取り除いて検索)。
     */
    @Nullable
    public static EnumDecoration fromBaseName(String registryName) {
        for (EnumDecoration deco : values()) {
            if (registryName.equals(deco.name)
                    || registryName.equals(deco.getSlabName())
                    || registryName.equals(deco.getDoubleSlabName())
                    || registryName.equals(deco.getStairsName())) {
                return deco;
            }
        }
        return null;
    }
}
