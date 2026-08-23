package ruby.bamboo.block.decoration;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/**
 * デコレーション用階段。旧 DecorationStairs (1.10.2) の移植。
 */
public class DecorationStairsBlock extends StairBlock {

    private final EnumDecoration deco;

    public DecorationStairsBlock(EnumDecoration deco, BlockState baseState) {
        super(baseState, DecorationBlocks.props(deco));
        this.deco = deco;
    }

    public EnumDecoration getDeco() {
        return this.deco;
    }
}
