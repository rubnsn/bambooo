package ruby.bamboo.block.decoration;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * デコレーション系の共通プロパティ。旧 DecorationSlab/Stairs の material/hardness 引継ぎ。
 */
public final class DecorationBlocks {

    /** 旧版と同じ hardness 0.5 / resistance 300 */
    public static BlockBehaviour.Properties props(EnumDecoration deco) {
        return BlockBehaviour.Properties.of()
                .mapColor(DecorationBlock.mapColorOf(deco))
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE)
                .strength(0.5f, 300f);
    }

    private DecorationBlocks() {
    }
}
