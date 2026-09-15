package ruby.bamboo.block;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.block.entity.MillBlockEntity;

/**
 * 風車・水車 (旧 EntityMill 系 EntityWindmill / EntityWaterwheel の 1.20.1 移植を 1.21.1-NeoForge へ)。
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
    public enum Type implements StringRepresentable {
        /** 風車 (通常)。textures/entity/windmill.png */
        WINDMILL(false, false),
        /** 風車 (布張り)。textures/entity/windmill_cloth.png */
        WINDMILL_CLOTH(false, true),
        /** 水車。textures/entity/waterwheel.png */
        WATERWHEEL(true, false);

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        /** 水車か (水没時のみ回転する) */
        public final boolean waterwheel;
        /** 布張り風車か */
        public final boolean cloth;

        Type(boolean waterwheel, boolean cloth) {
            this.waterwheel = waterwheel;
            this.cloth = cloth;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final MapCodec<MillBlock> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    Type.CODEC.fieldOf("type").forGetter(MillBlock::getType),
                    BlockBehaviour.propertiesCodec())
                    .apply(inst, MillBlock::new));

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** サイズ: 0=S / 1=M / 2=L (描画スケール 0.5 / 1.0 / 1.5) */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 2);
    /** true で逆回転 */
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    /** blockstate/blockstates/windmill.json 等 (mill_dummy 単一モデル) 用の ID 接尾辞 */
    public static final ResourceLocation WINDMILL_ID = ResourceLocation.fromNamespaceAndPath(ruby.bamboo.BambooMod.MODID, "windmill");
    public static final ResourceLocation WINDMILL_CLOTH_ID = ResourceLocation.fromNamespaceAndPath(ruby.bamboo.BambooMod.MODID, "windmill_cloth");
    public static final ResourceLocation WATERWHEEL_ID = ResourceLocation.fromNamespaceAndPath(ruby.bamboo.BambooMod.MODID, "waterwheel");

    private final Type type;

    public MillBlock(Type type, BlockBehaviour.Properties props) {
        super(props);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, SIZE, REVERSED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        // 旧 EntityMill#setDir (プレイヤー方向) 相当。車輪面がプレイヤーと正対する
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /**
     * 右クリックで回転方向を反転、スニーク右クリックでサイズ S→M→L→S を循環。
     * 旧版の竹・つづら持ち替えギミックの代替 (素手・何持ちでも可)。
     * 1.21 分割: 手持ちありはこちら。
     */
    @Override
    public ItemInteractionResult useItemOn(ItemStack useStack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (useStack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        toggle(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(false);
    }

    /**
     * 1.21 分割: 空手はこちら。素手右クリックでも反転/サイズ切替できる (旧 use 相当)。
     */
    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        toggle(state, level, pos, player);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    private static void toggle(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        BlockState next = player.isShiftKeyDown()
                ? state.setValue(SIZE, (state.getValue(SIZE) + 1) % 3)
                : state.cycle(REVERSED);
        level.setBlock(pos, next, 3);
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
        // 回転は純粋なクライアント演出のためサーバーティッカーは不要。
        // BE 型は親の BambooBlockEntities.MILL_BE 配線後に解決される (未配線なら null で tick 無し)。
        if (!level.isClientSide) {
            return null;
        }
        BlockEntityType<MillBlockEntity> millType = MillBlockEntity.lookupType();
        if (millType == null) {
            return null;
        }
        return createTickerHelper(type, millType, MillBlockEntity::tick);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
