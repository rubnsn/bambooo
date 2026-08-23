package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 畳。旧 Tatami (1.10.2) の移植。
 * <p>
 * 旧版は meta 0-3 (通常/縁無し/日焼け/縁無し日焼け) を1ブロックで持っていたが、
 * 1.20.1では4種を独立ブロックとして登録し、向きは facing(水平4方向)で表現する。
 */
public class TatamiBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    /** 旧meta相当のバリアント */
    public enum Variant {
        /** meta=0 通常 */
        NORMAL("tatami"),
        /** meta=1 縁無し */
        NON_BORDER("tatami_ns"),
        /** meta=2 日焼け */
        TAN("tatami_tan"),
        /** meta=3 縁無し+日焼け */
        TAN_NON_BORDER("tatami_tan_ns");

        public final String modelName;

        Variant(String modelName) {
            this.modelName = modelName;
        }
    }

    private final String descriptionId;

    public TatamiBlock(Variant variant) {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.GRASS)
                .strength(0.5f)
                .noOcclusion());
        this.descriptionId = "block.bamboomod." + variant.modelName.replace('_', '.');
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
