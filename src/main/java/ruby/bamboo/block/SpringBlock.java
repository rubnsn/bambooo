package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.block.entity.SpringWaterBlockEntity;

/**
 * 源泉ブロック — 1.20.1移植版。
 * ACTIVE トグルで直上に温泉水を湧出させる。満水で停止、隣接変化で再開。
 */
public class SpringBlock extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public SpringBlock() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(1.0f, 300.0f));
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(ACTIVE)) {
            level.scheduleTick(pos, this, getSourceDelay());
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.is(BambooBlocks.SPRING_WATER.get())) {
            // 満水チェックと水位+1
            boolean up = SpringWaterBlockEntity.levelUp(level, above, aboveState);
            if (up) {
                level.scheduleTick(above, BambooBlocks.SPRING_WATER.get(), getWaterDelay());
            } else {
                // 満水: 源泉自身は再スケジュールせず停止 (旧準拠)
                return;
            }
        } else if (level.isEmptyBlock(above)) {
            BlockState newState = BambooBlocks.SPRING_WATER.get().defaultBlockState()
                    .setValue(SpringWaterBlock.SPRING_LEVEL, 1);
            level.setBlock(above, newState, 3);
            // BEがあれば親を自分(湧出源)に設定
            if (level.getBlockEntity(above) instanceof SpringWaterBlockEntity be) {
                be.setParent(above);
            }
            level.scheduleTick(above, BambooBlocks.SPRING_WATER.get(), getWaterDelay());
        } else {
            level.setBlock(pos, state.setValue(ACTIVE, Boolean.FALSE), 3);
            // 上が塞がれたら停止、再スケジュールしない
            return;
        }
        if (state.getValue(ACTIVE)) {
            level.scheduleTick(pos, this, getSourceDelay());
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && state.getValue(ACTIVE)) {
            level.scheduleTick(pos, this, getSourceDelay());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean active = !state.getValue(ACTIVE);
        level.setBlock(pos, state.setValue(ACTIVE, active), 3);
        if (active) {
            level.scheduleTick(pos, this, getSourceDelay());
        } else {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.is(BambooBlocks.SPRING_WATER.get())) {
                if (level.getBlockEntity(above) instanceof SpringWaterBlockEntity be) {
                    be.setDead();
                    // 即時乾燥スケジュールを開始
                    level.scheduleTick(above, BambooBlocks.SPRING_WATER.get(), getEvaporationDelay());
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ===== 形状回転は Block 規約で public =====
    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    private int getSourceDelay() {
        try {
            return SpringConfig.COMMON.sourceTickDelay.get();
        } catch (Exception e) {
            return 20;
        }
    }

    private int getWaterDelay() {
        try {
            return SpringConfig.COMMON.waterTickDelay.get();
        } catch (Exception e) {
            return 20;
        }
    }

    private int getEvaporationDelay() {
        try {
            return SpringConfig.COMMON.evaporationDelay.get();
        } catch (Exception e) {
            return 30;
        }
    }
}
