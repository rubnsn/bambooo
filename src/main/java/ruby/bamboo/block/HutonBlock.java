package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.entity.ChairEntity;

/**
 * 布団 (旧 Huton の1.20.1移植)。
 * <p>
 * BedBlock継承で二連ブロック(OCCUPIED/PART/FACING)・ベッド爆発・リスポーン地点挙動を継承しつつ、
 * 旧仕様の「昼は椅子に座って時間加速」を再現する。
 * <p>
 * 形状: (0,0,0)-(16,4,16) 固定でHEAD/FOOT同形状 (旧Huton.AABB=0.25高=4px)
 * <p>
 * 設置: 足元完全不透明必須 (旧ItemHuton 2ブロック分 isFullyOpaque 条件)
 */
public class HutonBlock extends BedBlock {

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    private static final int TIME_ACC = 1200;

    public HutonBlock() {
        super(DyeColor.WHITE, BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .sound(SoundType.WOOL)
                .strength(0.5F, 300.0F)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, BedPart.FOOT)
                .setValue(OCCUPIED, false)
                .setValue(FACING, Direction.NORTH));
    }

    // ===== 形状 (旧 getBoundingBox) =====

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // ===== Bed判定 (isBed) =====

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity player) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 布団はBlockEntity不要。BedBlockはBedBlockEntityを生成するが、モデル描画はMODELで行うため不要。
     * nullを返すことで無駄なBE生成を抑止。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    // ===== 設置: 足元完全不透明チェック (旧ItemHuton) =====

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        // 旧: world.getBlockState(pos.down()).isFullyOpaque() && getBlockState(blockpos.down()).isFullyOpaque()
        // 1.20.1では isFaceSturdy(DOWN) で完全不透明面判定
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        // 二連部分の足元も要チェックのため、canSurviveは配置時 setPlacedBy 前の単体チェックでは不十分。
        // 追加で getStateForPlacement 側でも replaceable/borderチェックが行われるため、ここでは自位置のみ。
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // vanilla BedBlock#getStateForPlacement は「隣がreplaceableかつWorldBorder内ならFOOTを返す」のみ。
        // 旧ItemHutonの「両位置がreplaceable/airかつ両位置下がisFullyOpaque」条件を付与する。
        Direction direction = ctx.getHorizontalDirection();
        BlockPos pos = ctx.getClickedPos();
        BlockPos headPos = pos.relative(direction);
        Level level = ctx.getLevel();
        // replaceable / air チェック (旧 flag2/flag3)
        BlockState posState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(headPos);
        boolean flag2 = posState.canBeReplaced(ctx) || level.isEmptyBlock(pos);
        boolean flag1 = headState.canBeReplaced(ctx) || level.isEmptyBlock(headPos);
        if (!flag2 || !flag1) {
            return null;
        }
        if (!level.getWorldBorder().isWithinBounds(headPos)) {
            return null;
        }
        // 足元完全不透明 (旧 isFullyOpaque 2ブロック分)
        BlockPos below = pos.below();
        BlockPos headBelow = headPos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        if (!level.getBlockState(headBelow).isFaceSturdy(level, headBelow, Direction.UP)) {
            return null;
        }
        // editability は Item側でチェックされるが、念のためここでも返すだけ
        return this.defaultBlockState().setValue(FACING, direction).setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false);
    }

    // ===== 右クリック (旧 onBlockActivated) =====

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // HEADクリックならFOOTへ補正 (BedBlock:76 と同様)
        if (state.getValue(PART) != BedPart.FOOT) {
            pos = pos.relative(state.getValue(FACING).getOpposite());
            state = level.getBlockState(pos);
            if (!state.is(this)) {
                return InteractionResult.CONSUME;
            }
        }

        // 次元でベッドが機能しない場合(ネザー等)は爆発しない


        // 使用中なら村人追い出し or 占有メッセージ (BedBlock#kickVillagerOutOfBed は private のため inline)
        if (state.getValue(OCCUPIED)) {
            if (!kickVillagerOutOfBed(level, pos)) {
                player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // 昼間 (= NOT_POSSIBLE_NOW) は椅子に座る
        // 旧: player.trySleep(pos)==NOT_POSSIBLE_NOW 判定、1.20.1では level.isDay() で近似
        if (level.isDay()) {
            ChairEntity chair = new ChairEntity(BambooEntities.HUTON_CHAIR.get(), level);
            chair.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            chair.setListener((world, entity) -> {
                // 5秒ごと (worldTime%100==0) に時間加速 +60秒 (1201tick)
                if (world.getDayTime() % 100 == 0) {
                    if (world instanceof ServerLevel serverLevel) {
                        long newTime = serverLevel.getDayTime() + TIME_ACC + 1;
                        serverLevel.setDayTime(newTime);
                        try {
                            net.minecraft.world.level.storage.ServerLevelData sld = (net.minecraft.world.level.storage.ServerLevelData) serverLevel.getLevelData();
                            if (serverLevel.isRaining()) {
                                int rainTime = sld.getRainTime();
                                if (rainTime > TIME_ACC) {
                                    sld.setRainTime(rainTime - TIME_ACC);
                                }
                            }
                            if (serverLevel.isThundering()) {
                                int thunderTime = sld.getThunderTime();
                                if (thunderTime > TIME_ACC) {
                                    sld.setThunderTime(thunderTime - TIME_ACC);
                                }
                            }
                            if (!entity.getPassengers().isEmpty() && entity.getPassengers().get(0) instanceof Player p) {
                                long t = serverLevel.getDayTime();
                                int hour = (int) (((t + 6000) % 24000) / 1000);
                                int minute = (int) (((t + 6000) % 600) / 10);
                                String msg = hour + ":" + String.format("%02d", minute * 10)
                                        + (serverLevel.isRaining() ? " RainTime at" + sld.getRainTime() : "")
                                        + (serverLevel.isThundering() ? " ThunderTime at" + sld.getThunderTime() : "");
                                p.displayClientMessage(Component.literal(msg), false);
                            }
                        } catch (ClassCastException e) {
                            // ignore
                        }
                    }
                }
            });
            level.addFreshEntity(chair);
            player.startRiding(chair);
            return InteractionResult.SUCCESS;
        } else {
            // 夜間は通常の睡眠処理へ委譲
            return super.use(state, level, pos, player, hand, hit);
        }
    }

    // updateShape / playerWillDestroy は BedBlockの二連同期をそのまま利用
    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
            BlockPos currentPos, BlockPos facingPos) {
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    private boolean kickVillagerOutOfBed(Level level, BlockPos pos) {
        java.util.List<Villager> list = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        if (list.isEmpty()) {
            return false;
        } else {
            list.get(0).stopSleeping();
            return true;
        }
    }

    // getMapColor は不要だが旧MapColor.AIR相当でNONEをプロパティで指定済み
}
