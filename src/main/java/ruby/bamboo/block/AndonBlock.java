package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 行灯。旧 Andon (1.10.2) の移植。
 * <p>
 * 小型の行灯ブロック。光レベル14。
 * 下にブロックがある場合は脚(4本)付きモデル (ON_GROUND=true)、
 * 空中/壁面の場合は脚なしモデルで描画される (旧 getActualState 相当)。
 */
public class AndonBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 下が非空気なら true (脚表示) - 旧 getActualState の on_ground 相当 */
    public static final BooleanProperty ON_GROUND = BooleanProperty.create("on_ground");

    /** 旧 AABB [0.25,0.2,0.25]-[0.75,0.925,0.75] */
    private static final VoxelShape SHAPE = Block.box(5, 3, 5, 12, 15, 12);

    public AndonBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ON_GROUND, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(ON_GROUND);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧 onBlockPlacedBy: プレイヤーの逆方向へ向く
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                // 旧 getActualState: 下が空気でなければ ON_GROUND=true
                .setValue(ON_GROUND, !context.getLevel().isEmptyBlock(context.getClickedPos().below()));
    }

    /**
     * 旧 getActualState 相当: 下ブロックの変化で ON_GROUND を更新する。
     */
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN) {
            return state.setValue(ON_GROUND, !level.isEmptyBlock(pos.below()));
        }
        return state;
    }

    /** 旧 randomDisplayTick 相当は無し (旧コードでもパーティクル無し)。光のみ。 */

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
