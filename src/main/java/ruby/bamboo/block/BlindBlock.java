package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * すだれ (blind)。sakuraの Blind 相当を IronBarsBlock で移植。
 * <p>
 * 透過テクスチャ (cutout) かつ collision空 (getCollisionShape = empty)。
 * 旧 sakura PaneBlock は blocksMovement == false のとき collision を empty にしていた。
 */
public class BlindBlock extends IronBarsBlock {

    public BlindBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.WOOD)
                .strength(0.5F)
                .noOcclusion()
                .noCollission());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }
}
