package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.SlideDoorBlockEntity;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 引き戸 (SlideDoor) — sakura-master `com.ruby.meshi.block.SlideDoor` の 1.20.1 移植。
 * <p>
 * 旧 Bamboo は Entity だったが、sakura-master は {@link DoorBlock} 継承 + {@link SlideDoorBlockEntity} で
 * 横スライド描画を実現している。本移植は sakura準拠 7種 (shoji 3種+husuma+glass2種+yukimi) を踏襲する。
 * 透過3種は translucent、他は solid/cutout 相当だが RenderType は BambooClientSetup で付与する。
 */
public class SlideDoorBlock extends DoorBlock implements net.minecraft.world.level.block.EntityBlock {

    public static final BooleanProperty MIRROR = BooleanProperty.create("mirror");
    public static final BooleanProperty MOVED = BooleanProperty.create("moved");

    // sakura 1.16 の AABB をそのまま (厚さ1 = 7-8 or 8-9)
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 7.0D, 16.0D, 16.0D, 8.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 8.0D, 16.0D, 16.0D, 9.0D);
    protected static final VoxelShape WEST_AABB = Block.box(8.0D, 0.0D, 0.0D, 9.0D, 16.0D, 16.0D);
    protected static final VoxelShape EAST_AABB = Block.box(7.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D);

    // 種別によるレンダーレイヤー制御用フラグ (透過かどうか)
    private final boolean translucent;

    public SlideDoorBlock(Properties properties, boolean translucent) {
        // DoorBlock は BlockSetType を要求する。音は sakura準拠で無音にしたいが DoorBlock は type から音を取るため OAK を渡し、playSound を無効化する
        super(properties, BlockSetType.OAK);
        this.translucent = translucent;
        this.registerDefaultState(this.defaultBlockState().setValue(MIRROR, false).setValue(MOVED, false));
    }

    public SlideDoorBlock(Properties properties) {
        this(properties, false);
    }

    public boolean isTranslucent() {
        return translucent;
    }

    public static Properties createProp(boolean translucent) {
        // sakura: createMiscPropety() Material.GLASS hardness1 SoundType.CLOTH -> 1.20.1 では WOOD/ WOOL 相当で再現, 透過差は noOcclusion + RenderType で制御
        return Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(translucent ? SoundType.GLASS : SoundType.WOOD)
                .strength(1.0F)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false);
    }

    // ===== BlockState =====

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MIRROR, MOVED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction direction = state.getValue(FACING);
        return switch (direction) {
            case EAST -> EAST_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case NORTH -> NORTH_AABB;
            default -> EAST_AABB;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : state.getShape(level, pos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(OPEN) || state.getValue(MOVED) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    // ===== EntityBlock (TileEntity) =====

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // 常時BEを返してtickと描画を保証する。sakuraは OPEN||MOVED のみだったが、1.20.1では常時BEの方が安定し、
        // 閉じている間の描画は Renderer 側で early return するため無駄は無い
        return new SlideDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == BambooBlockEntities.SLIDE_DOOR_BE.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<SlideDoorBlockEntity>) SlideDoorBlockEntity::tick;
        }
        return null;
    }

    // ===== 開閉ロジック (sakura準拠、音無し) =====

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // 両halfを同時にトグルする
        BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        boolean newOpen = !state.getValue(OPEN);
        BlockState newState = state.setValue(OPEN, newOpen);
        BlockState newOtherState = otherState.is(this) ? otherState.setValue(OPEN, newOpen) : otherState;
        level.setBlock(pos, newState, 10);
        if (otherState.is(this)) {
            level.setBlock(otherPos, newOtherState, 10);
        }
        onMove(newState, level, pos);
        if (otherState.is(this)) {
            onMove(newOtherState, level, otherPos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(this) && state.getValue(OPEN) != open) {
            BlockState newState = state.setValue(OPEN, open);
            level.setBlock(pos, newState, 10);
            onMove(newState, level, pos);
            // sound 無し
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        boolean flag = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (block != this && flag != state.getValue(POWERED)) {
            // sakura: flag != OPEN の時だけ onMove + sound。1.20.1 の DoorBlock は同様だが flag != OPEN で gameEvent も出す
            if (flag != state.getValue(OPEN)) {
                onMove(state, level, pos);
            }
            level.setBlock(pos, state.setValue(POWERED, flag).setValue(OPEN, flag), 2);
            // sound 無し
        }
    }

    void onMove(BlockState state, Level level, BlockPos pos) {
        // 1.20.1では newBlockEntity が常時BEを返すため、明示的な setTileEntity は不要。
        // ただし上半分の BE も生成されるように、もう一方の half にも更新を伝播させる
        // neighborChanged / setBlock で自動でBEが生成されるため何もしない
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        if (pos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(pos.above()).canBeReplaced(ctx)) {
            boolean flag = world.hasNeighborSignal(pos) || world.hasNeighborSignal(pos.above());
            Direction facing = ctx.getHorizontalDirection();
            DoorHingeSide hinge = getHingeSide(ctx);
            return this.defaultBlockState().setValue(FACING, facing).setValue(HINGE, hinge).setValue(POWERED, flag)
                    .setValue(OPEN, flag).setValue(HALF, DoubleBlockHalf.LOWER).setValue(MIRROR, isMirrorTexture(ctx));
        } else {
            return null;
        }
    }

    DoorHingeSide getHingeSide(BlockPlaceContext ctx) {
        // DoorBlock.getHinge は private なので sakura のロジックをそのままコピー (SlideDoor.java:150)
        BlockPos blockpos = ctx.getClickedPos();
        Direction direction = ctx.getHorizontalDirection();
        int j = direction.getStepX();
        int k = direction.getStepZ();
        Vec3 vec3d = ctx.getClickLocation();
        double d0 = vec3d.x - blockpos.getX();
        double d1 = vec3d.z - blockpos.getZ();
        return (j >= 0 || !(d1 < 0.5D)) && (j <= 0 || !(d1 > 0.5D)) && (k >= 0 || !(d0 > 0.5D)) && (k <= 0 || !(d0 < 0.5D)) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
    }

    boolean isMirrorTexture(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        Direction placerFacing = ctx.getHorizontalDirection();
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState state = world.getBlockState(pos.relative(dir));
            if (state.getBlock() instanceof SlideDoorBlock) {
                Direction facing = state.getValue(FACING);
                boolean isMirror = state.getValue(MIRROR);
                if (facing == placerFacing.getOpposite() && !isMirror) {
                    return true;
                }
                if (facing == placerFacing && isMirror) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // DoorBlock のクリエイティブ破壊時の上半分ドロップ防止を呼びつつ、追加処理なし
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // 上半分も含めて BE を持つ可能性があるので、BE があれば削除は tick 側に任せるが、念のため superを呼ぶ
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        if (state.getValue(OPEN)) {
            return state;
        }
        return super.rotate(state, rot);
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        if (state.getValue(OPEN)) {
            return state;
        }
        return super.mirror(state, mirror);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    // TallBlockItem 用ヘルパ (BambooBlocks 側で使用)
    public static BlockItem createBlockItem(Block block, Item.Properties props) {
        return new DoubleHighBlockItem(block, props);
    }
}
