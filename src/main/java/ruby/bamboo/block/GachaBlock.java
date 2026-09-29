package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
 * GUIなし分離式: ガチャコイン手持ちで右クリック→投入、
 * コイン投入済みでコイン以外を持って右クリック→ハンドルを回してカプセル排出。
 * カプセル (赤60/青30/黄9/虹1) は手に持って右クリックで開封し、中身は
 * カプセル色ごとの期待値で抽選される。開封後の空カプセルは手元に残る。
 * ドロップはユーザー設置LOWERのみ (loot_table の half=lower+user_placed 条件)。
 * 投入済みコインは破壊時に返却する。
 */
public class GachaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /**
     * ユーザー設置マーカー。true=ユーザー設置 (通常硬度・通常ドロップ)。
     * false=構造物生成分 (超硬度・爆破耐性・無ドロップ)。BreakEventに頼らず硬度/耐性/戦利品で表現する。
     */
    public static final BooleanProperty USER_PLACED = BooleanProperty.create("user_placed");

    private static final VoxelShape LOWER_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    private static final VoxelShape UPPER_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public GachaBlock() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .sound(SoundType.METAL)
                // ユーザー設置分の基礎値 (硬度1.5・爆破耐性1200)。生成分は下のoverrideで上乗せ
                .strength(1.5F, 1200F));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(USER_PLACED, Boolean.valueOf(false)));
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
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(USER_PLACED, Boolean.valueOf(false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
            ItemStack stack) {
        BlockState placed = state.setValue(USER_PLACED, Boolean.valueOf(true));
        level.setBlock(pos, placed, 3);
        level.setBlock(pos.above(), placed.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /**
     * 生成分は50倍硬い (手掘りは可能だが実質的に割れない)。ユーザー設置分は基礎値通り。
     * クリエイティブ破壊は別経路のため影響なし。
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player,
            net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        float progress = super.getDestroyProgress(state, player, level, pos);
        if (!state.getValue(USER_PLACED)) {
            progress *= 0.02F;
        }
        return progress;
    }

    /**
     * 生成分は爆破で壊れない (岩盤級)。ユーザー設置分は基礎値 (1200) 通り。
     */
    @Override
    public float getExplosionResistance(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, Explosion explosion) {
        if (!state.getValue(USER_PLACED)) {
            return 3600000.0F;
        }
        return super.getExplosionResistance();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // LOWER は土台不要 (下掘りでも崩落しない。構造物ガチャ保護のため)
        return true;
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
        builder.add(FACING, HALF, USER_PLACED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(lowerPos) instanceof GachaBlockEntity be) {
            ItemStack held = player.getItemInHand(hand);
            boolean isCoin = held.is(ruby.bamboo.core.init.BambooItems.GACHA_COIN.get());
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (!be.hasCoin() && isCoin) {
                // 第一段: コイン投入
                if (!player.isCreative()) {
                    held.shrink(1);
                }
                be.setHasCoin(true);
                level.sendBlockUpdated(lowerPos, state, level.getBlockState(lowerPos), 3);
                level.playSound(null, lowerPos, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.4F);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.bamboomod.gacha_insert"),
                        true);
                return InteractionResult.SUCCESS;
            }
            if (be.hasCoin() && !isCoin) {
                // 第二段: ハンドル操作→カプセル排出 (中身は開封時に抽選)
                be.setHasCoin(false);
                level.sendBlockUpdated(lowerPos, state, level.getBlockState(lowerPos), 3);
                level.blockEvent(lowerPos, this, 1, 0);
                ruby.bamboo.gacha.GachaCapsule capsule =
                        ruby.bamboo.gacha.GachaCapsule.rollCapsule(level.getRandom());
                net.minecraft.world.item.ItemStack out =
                        ruby.bamboo.item.GachaCapsuleItem.create(capsule, false);
                if (!player.getInventory().add(out.copy())) {
                    // 排出口 (前面中央) にドロップ
                    net.minecraft.core.Direction f = state.getValue(FACING);
                    double dx = lowerPos.getX() + 0.5 + f.getStepX() * 0.7;
                    double dy = lowerPos.getY() + 0.35;
                    double dz = lowerPos.getZ() + 0.5 + f.getStepZ() * 0.7;
                    net.minecraft.world.entity.item.ItemEntity e =
                            new net.minecraft.world.entity.item.ItemEntity(level, dx, dy, dz,
                                    out.copy());
                    e.setDefaultPickUpDelay();
                    level.addFreshEntity(e);
                }
                net.minecraft.sounds.SoundEvent se = switch (capsule) {
                    case RAINBOW -> net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP;
                    case YELLOW -> net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP;
                    case BLUE -> net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
                    default -> net.minecraft.sounds.SoundEvents.BUNDLE_INSERT;
                };
                level.playSound(null, lowerPos, se, net.minecraft.sounds.SoundSource.BLOCKS,
                        0.6F, capsule == ruby.bamboo.gacha.GachaCapsule.RAINBOW ? 1.0F : 1.2F);
                // ハンドルを回すガチャ感: フェンスゲート閉音を少し低めに重ねる
                level.playSound(null, lowerPos,
                        net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.8F);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.bamboomod.gacha_capsule_eject",
                                net.minecraft.network.chat.Component.translatable(
                                        capsule.langKey()).getString()),
                        false);
                return InteractionResult.SUCCESS;
            }
            if (!be.hasCoin()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.bamboomod.gacha_need_coin"),
                        true);
            } else {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.bamboomod.gacha_already"),
                        true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean moved) {
        // 投入済みコインは返却 (LOWER破壊時のみ・BE消去前)
        if (!state.is(newState.getBlock())
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && level.getBlockEntity(pos) instanceof GachaBlockEntity be
                && be.hasCoin()) {
            be.setHasCoin(false);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    new ItemStack(ruby.bamboo.core.init.BambooItems.GACHA_COIN.get()));
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
