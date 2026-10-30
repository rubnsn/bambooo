package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * ハーフ畳。通常畳 ({@link TatamiBlock}) と同じ向き (facing) を持つ Slab。
 * 織り目の向きを合わせるため、設置向きの扱いも通常畳と同一 (時計回り90度)。
 */
public class TatamiSlabBlock extends SlabBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** randomTick 毎の日焼け確率 (25%) */
    private static final float TAN_PROBABILITY = 0.25F;

    public TatamiSlabBlock(BlockBehaviour.Properties props) {
        super(props.randomTicks());
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState base = super.getStateForPlacement(context);
        if (base == null) {
            return null;
        }
        return base.setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    /**
     * 日焼け変化。直射日光 (昼かつ上空が空に開けている) の下でのみ 25% で
     * tatami_slab → tatami_tan_slab へ一方通行で変化する。
     * 向き (FACING) と halve (TYPE) は維持する。
     */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        if (state.getBlock() != BambooBlocks.TATAMI_SLAB.get()) {
            return;
        }
        if (!level.isDay()) {
            return;
        }
        if (!level.canSeeSky(pos.above())) {
            return;
        }
        if (rand.nextFloat() >= TAN_PROBABILITY) {
            return;
        }
        BlockState target = BambooBlocks.TATAMI_TAN_SLAB.get().defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(SlabBlock.TYPE, state.getValue(SlabBlock.TYPE));
        level.setBlock(pos, target, 3);
    }
}
