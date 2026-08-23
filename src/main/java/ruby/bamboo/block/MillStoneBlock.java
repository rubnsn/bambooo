package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.block.entity.MillStoneBlockEntity;

/**
 * 石臼 (旧 MillStone の移植)。
 * <p>
 * 旧仕様: Material.ROCK / 硬度1 / 耐爆300 / FACING無し(方向不問) /
 * 非不透明・非フルキューブ / TESR描画。
 * <p>
 * GRIND_MOTION (0-3) は旧 META 相当の粉砕モーション表示用プロパティ
 * (旧 @StateIgnore 相当でアイテム meta 化はされない)。
 * 描画は {@link RenderShape#INVISIBLE} + BER
 * ({@link ruby.bamboo.block.entity.MillStoneBlockRenderer}) で行うため、
 * チャンクメッシュには現れない。
 */
public class MillStoneBlock extends BaseEntityBlock {

    /** 粉砕モーション (0-3)。旧 PropertyInteger META の実使用範囲 */
    public static final IntegerProperty GRIND_MOTION = IntegerProperty.create("grind_motion", 0, 3);

    /**
     * 粉砕稼働中フラグ。
     * <p>
     * 旧版は META(grindMotion)のみで回転を制御していたが、grindMotion は
     * {@code grindTime%40/10} の循環のため 40tick 中 10tick が 0 になり、
     * クライアントがそれを「停止」と誤認して回転がリセットされる問題があった。
     * GRINDING を別プロパティとすることで、稼働中は motion==0 の瞬間も連続回転させる。
     */
    public static final BooleanProperty GRINDING = BooleanProperty.create("grinding");

    public MillStoneBlock() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                // 旧 hardness=1 / resistance=300
                .strength(1.0f, 300.0f)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(GRIND_MOTION, 0)
                .setValue(GRINDING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GRIND_MOTION).add(GRINDING);
    }

    // ===== GUI オープン (旧 onBlockActivated 相当) =====

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MillStoneBlockEntity mill) {
            player.openMenu(mill);
        }
        return InteractionResult.CONSUME;
    }

    // ===== BlockEntity =====

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // TESR相当: ブロックモデルは描画せずBERで描く
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MillStoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, ruby.bamboo.core.init.BambooBlockEntities.MILL_STONE_BE.get(),
                    MillStoneBlockEntity::tick);
        }
        return createTickerHelper(type, ruby.bamboo.core.init.BambooBlockEntities.MILL_STONE_BE.get(),
                MillStoneBlockEntity::tick);
    }

    /** 破壊時に中身をドロップする (バニラ ChestBlock.onRemove 相当) */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MillStoneBlockEntity mill) {
                mill.dropContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}