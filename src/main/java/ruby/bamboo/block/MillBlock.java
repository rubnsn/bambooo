package ruby.bamboo.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.block.entity.MillBlockEntity;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * 風車・水車 (旧 EntityMill 系 EntityWindmill / EntityWaterwheel の 1.20.1 移植)。
 * <p>
 * 旧版 (1.7 頃) は {@code EntityMill} 基底の Entity 式だったが、引き戸と同様に
 * Block + BlockEntity + BER 方式へ移行した。回転は装飾のみ (無機能)。
 * <ul>
 * <li>風車 ({@link Type#WINDMILL} / {@link Type#WINDMILL_CLOTH}): 常時回転。
 * 回転速度はクライアント側 static な風速でランダム変化
 * ({@link MillBlockEntity} 参照、旧の毎tick+1固定から変更)。</li>
 * <li>水車 ({@link Type#WATERWHEEL}): 自身・真下・水平隣・その真下のいずれかに
 * 水があるときのみ回転 (旧 boundingBox の水判定相当)。</li>
 * </ul>
 * 右クリックで回転方向を反転 (REVERSED)、スニーク右クリックでサイズ S/M/L
 * (SIZE 0/1/2、描画スケール 0.5/1.0/1.5) を切替える。旧版の竹・つづら持ち替え
 * ギミック (羽根枚数 4-8・サイズ 1-5・つづら消費) は廃止し、羽根は4枚固定。
 * サイズ M/L は1ブロックからはみ出して描画される (BER + INFINITE 境界)。
 */
public class MillBlock extends BaseEntityBlock {

    /** 風車2種・水車の種別。見た目 (モデル・テクスチャ) と回転条件を切替える。 */
    public enum Type {
        /** 風車 (通常)。textures/entity/windmill.png */
        WINDMILL(false, false),
        /** 風車 (布張り)。textures/entity/windmill_cloth.png */
        WINDMILL_CLOTH(false, true),
        /** 水車。textures/entity/waterwheel.png */
        WATERWHEEL(true, false);

        /** 水車か (水没時のみ回転する) */
        public final boolean waterwheel;
        /** 布張り風車か */
        public final boolean cloth;

        Type(boolean waterwheel, boolean cloth) {
            this.waterwheel = waterwheel;
            this.cloth = cloth;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** サイズ: 0=S / 1=M / 2=L (描画スケール 0.5 / 1.0 / 1.5) */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 2);
    /** true で逆回転 */
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    private final Type type;

    public MillBlock(Type type) {
        super(Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(1.0F)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SIZE, 1)
                .setValue(REVERSED, false));
    }

    public Type getType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, SIZE, REVERSED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧 EntityMill#setDir (プレイヤー方向) 相当。車輪面がプレイヤーと正対する
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /**
     * 右クリックで回転方向を反転、スニーク右クリックでサイズ S→M→L→S を循環。
     * 旧版の竹・つづら持ち替えギミックの代替 (素手・何持ちでも可)。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.CONSUME;
        }
        BlockState next = player.isShiftKeyDown()
                ? state.setValue(SIZE, (state.getValue(SIZE) + 1) % 3)
                : state.cycle(REVERSED);
        level.setBlock(pos, next, 3);
        return InteractionResult.CONSUME;
    }

    // ===== BlockEntity =====

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // モデル無し + BER 描画 (旧 TESR 相当)
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        // 回転は純粋なクライアント演出のためサーバーティッカーは不要
        if (level.isClientSide) {
            return createTickerHelper(type, BambooBlockEntities.MILL_BE.get(), MillBlockEntity::tick);
        }
        return null;
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
