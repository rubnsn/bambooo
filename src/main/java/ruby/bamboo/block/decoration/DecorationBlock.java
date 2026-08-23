package ruby.bamboo.block.decoration;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 無機能デコレーションブロック。旧 DecorationBlock (1.10.2) の移植。
 */
public class DecorationBlock extends Block {

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(SoundType.STONE)
                .strength(0.5f, 300f);
    }

    public DecorationBlock(EnumDecoration deco) {
        super(props(mapColorOf(deco)));
    }

    /**
     * RGB int を MapColor に変換する。1.20.1にはRGB直指定のAPIがないため
     * 近似の定義済み色へマッピングする。
     */
    static MapColor mapColorOf(EnumDecoration deco) {
        return switch (deco) {
            case KAWARA -> MapColor.COLOR_GRAY;
            case PLASTER -> MapColor.QUARTZ;
            case NAMAKO -> MapColor.DEEPSLATE;
            case WARA -> MapColor.COLOR_YELLOW;
            case KAYA -> MapColor.COLOR_BROWN;
            case CBIRCH -> MapColor.SAND;
            case COAK -> MapColor.WOOD;
            case CPINE -> MapColor.TERRACOTTA_BROWN;
        };
    }
}
