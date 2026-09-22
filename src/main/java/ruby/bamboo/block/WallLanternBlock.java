package ruby.bamboo.block;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 壁ランタン (新規)。
 * <p>
 * バニラランタンをベースに壁設置を可能とした光源 (光レベル15)。
 * 床置き・天井吊り・壁付けのいずれでも設置でき、支えが全て無くなると落下する。
 * 支柱はブロック上端 (y16) から吊るした鎖 + 頂部 (y14-16) のアームで構成し、
 * フェンス式に周囲4方向を探索して自動接続する (同種連結 or 堅固な面、細身2px)。
 * 鎖はバニラ吊りランタンと同一意匠 (lantern.png の交差プレート、cutout 描画)、
 * アームのみ黒のコンクリートパウダー。
 */
public class WallLanternBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final Map<Direction, BooleanProperty> PROP_BY_DIRECTION = Map.of(
            Direction.NORTH, NORTH,
            Direction.EAST, EAST,
            Direction.SOUTH, SOUTH,
            Direction.WEST, WEST);

    /** ランタン本体 (床置き)。バニラ LanternBlock.AABB と同一 */
    private static final VoxelShape BODY = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 7.0D, 11.0D),
            Block.box(6.0D, 7.0D, 6.0D, 10.0D, 9.0D, 10.0D));
    /** ランタン本体 (天井吊り)。バニラ LanternBlock.HANGING_AABB と同一 */
    private static final VoxelShape BODY_HANGING = Shapes.or(
            Block.box(5.0D, 1.0D, 5.0D, 11.0D, 8.0D, 11.0D),
            Block.box(6.0D, 8.0D, 6.0D, 10.0D, 10.0D, 10.0D));
    /** 支柱 (鎖): 調整中のため現在未参照。blockstate・判定とも外してある */
    private static final VoxelShape CHAIN = Block.box(7.0D, 8.0D, 7.0D, 9.0D, 16.0D, 9.0D);
    /** 支柱アーム (細身2px・頂部y14-16)。中央の鎖と繋がり隣ブロック面まで伸ばす */
    private static final VoxelShape ARM_NORTH = Block.box(7.0D, 14.0D, 0.0D, 9.0D, 16.0D, 8.0D);
    private static final VoxelShape ARM_SOUTH = Block.box(7.0D, 14.0D, 8.0D, 9.0D, 16.0D, 16.0D);
    private static final VoxelShape ARM_EAST = Block.box(8.0D, 14.0D, 7.0D, 16.0D, 16.0D, 9.0D);
    private static final VoxelShape ARM_WEST = Block.box(0.0D, 14.0D, 7.0D, 8.0D, 16.0D, 9.0D);

    public WallLanternBlock() {
        // バニラランタン相当 (光15・ランタン音)。requiresCorrectToolForDrops は付けない
        // (mineableタグ未登録だと全ツールで不ドロップになるため)
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE).sound(SoundType.LANTERN)
                .strength(0.3F, 300F)
                .lightLevel(state -> 15)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HANGING, false)
                .setValue(WATERLOGGED, false)
                .setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING, WATERLOGGED, NORTH, EAST, SOUTH, WEST);
    }

    /**
     * フェンス式接続判定: 同種ブロック同士は連結し、それ以外は堅固な面に支柱を伸ばす。
     */
    private boolean connectsTo(BlockState neighbor, boolean sturdyFace) {
        return neighbor.getBlock() instanceof WallLanternBlock || sturdyFace;
    }

    private boolean connectedAt(BlockGetter level, BlockPos pos, Direction dir) {
        BlockPos neighborPos = pos.relative(dir);
        BlockState neighbor = level.getBlockState(neighborPos);
        return this.connectsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, dir.getOpposite()));
    }

    /** 吊り表示か (下支えなし = 天井吊り・壁付け浮遊時は吊りモデル。床置きのみ据え置き) */
    private static boolean hangingFor(LevelReader level, BlockPos pos) {
        return !Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState()
                .setValue(NORTH, this.connectedAt(level, pos, Direction.NORTH))
                .setValue(EAST, this.connectedAt(level, pos, Direction.EAST))
                .setValue(SOUTH, this.connectedAt(level, pos, Direction.SOUTH))
                .setValue(WEST, this.connectedAt(level, pos, Direction.WEST))
                .setValue(HANGING, hangingFor(context.getLevel(), pos))
                .setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
        // 床・天井・壁のいずれにも支えが無ければ設置不可 (バニラランタンと同様に null)
        return state.canSurvive(context.getLevel(), pos) ? state : null;
    }

    /**
     * 支え判定: 下支え (床置き)・上支え (天井吊り)・いずれかの壁面の堅固な面があれば成立。
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (Block.canSupportCenter(level, pos.below(), Direction.UP)) {
            return true;
        }
        if (Block.canSupportCenter(level, pos.above(), Direction.DOWN)) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos wallPos = pos.relative(dir);
            if (level.getBlockState(wallPos).isFaceSturdy(level, wallPos, dir.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        BlockState result = state;
        if (dir.getAxis().getPlane() == Direction.Plane.HORIZONTAL) {
            result = result.setValue(PROP_BY_DIRECTION.get(dir),
                    this.connectsTo(neighborState,
                            neighborState.isFaceSturdy(level, neighborPos, dir.getOpposite())));
        } else {
            result = result.setValue(HANGING, hangingFor(level, pos));
        }
        return !result.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : result;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // 吊り時はバニラ鎖つきモデル (判定は本体のみ、バニラ準拠)。据え置き時は鎖なし
        VoxelShape shape = state.getValue(HANGING) ? BODY_HANGING : BODY;
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, ARM_NORTH);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, ARM_EAST);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, ARM_SOUTH);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, ARM_WEST);
        }
        return shape;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return switch (rot) {
            case NONE -> state;
            case CLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            case CLOCKWISE_180 -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
        };
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case NONE -> state;
            case LEFT_RIGHT -> state
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(WEST, state.getValue(EAST));
            case FRONT_BACK -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(NORTH));
        };
    }
}
