package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.GachaBlockEntity;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * テスト用ブロックガチャポン (docs/port-spec-gacha.md)。
 * <p>
 * ドア・ベッドと同じ2ブロック背丈 (LOWER/UPPER)。描画は LOWER 側の BE が
 * フルBER ({@code GachaBlockRenderer}) で2マス分まとめて行う。
 * 右クリック (上下どちらでも) で Top GUI を開く (Menu-less Screen方式)。
 * ドロップは LOWER のみ (loot_table の half=lower 条件)。
 */
public class GachaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    private static final VoxelShape UPPER_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public GachaBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .sound(SoundType.METAL)
                .strength(1.5F, 30F));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 本体・ガチャ球・ツマミは BER で描画 (LOWER 側から2マス分)
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER_SHAPE : LOWER_SHAPE;
    }

    // ===== 2ブロック配置・破壊 (DoorBlock と同型) =====

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos npos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() != Direction.Axis.Y || half == DoubleBlockHalf.LOWER != (dir == Direction.UP)
                || neighbor.is(this) && neighbor.getValue(HALF) != half) {
            return half == DoubleBlockHalf.LOWER && dir == Direction.DOWN
                    && !state.canSurvive(level, pos)
                            ? Blocks.AIR.defaultBlockState()
                            : super.updateShape(state, dir, neighbor, level, pos, npos);
        }
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // クリエイティブで UPPER を壊したら LOWER も無ドロップで消す (DoorBlock と同型)
        if (!level.isClientSide && player.isCreative()) {
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockPos belowPos = pos.below();
                BlockState below = level.getBlockState(belowPos);
                if (below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.setBlock(belowPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, belowPos, Block.getId(below));
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        // ドア同様ピストンでは押せず壊れる
        return PushReaction.DESTROY;
    }

    // ===== BlockEntity は LOWER のみ =====

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new GachaBlockEntity(pos, state)
                : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (type != BambooBlockEntities.GACHA_BE.get()) {
            return null;
        }
        // クライアント演出のみ (ツマミ・球内回転)。サーバーtick不要
        return level.isClientSide
                ? (lvl, pos, st, be) -> GachaBlockEntity.tick(lvl, pos, st, (GachaBlockEntity) be)
                : null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            ruby.bamboo.client.handler.ClientGachaHandler.openTop();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
