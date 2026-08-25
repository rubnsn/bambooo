package ruby.bamboo.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * カットブロック — フルキューブの一部だけを使うブロック。
 * 中身は CutBlockEntity の cutState + yLevel/hLevel で管理。
 * 描画は INVISIBLE + BER で AABBに合わせたQuad再生成。
 * 空の場合は透明ダミー (Shapes.empty)。
 */
public class CutBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    public CutBlock() {
        super(Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f, 3.0f)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false)
                .isRedstoneConductor((s, l, p) -> false)
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
            be.setCutState(data.state());
            be.setLevels(data.yLevel(), data.hLevel());
            // FACINGは既にBlockState側に反映済みだが、BEのshapeCache無効化のため再設定
            be.invalidateShapeCache();
            // 空でない場合は更新を送信
            if (!be.isEmpty()) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // ===== BlockEntity =====

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CutBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BambooBlockEntities.CUT_BLOCK_BE.get(), CutBlockEntity::tick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    // ===== Shape =====

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (be.isEmpty()) {
                return Shapes.empty();
            }
            Direction facing = state.getValue(FACING);
            return be.getShapeCache(facing);
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        // 空なら透過、そうでなければAABBに応じて
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (be.isEmpty()) return true;
            // Yが16未満なら上部が空いているので透過
            return be.getYSize() < 16;
        }
        return false;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (be.isEmpty()) return 0;
            // 薄いほど光を透過
            if (be.getYSize() < 16 || be.getHSize() < 16) return 0;
        }
        return 0;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    // ===== Drops / Pick =====

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> list = super.getDrops(state, builder);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof CutBlockEntity cut) {
            if (!cut.isEmpty()) {
                ItemStack stack;
                if (list.isEmpty()) {
                    stack = new ItemStack(this);
                } else {
                    stack = list.get(0);
                }
                CompoundTag bet = new CompoundTag();
                cut.writeSyncData(bet);
                CompoundTag tag = stack.getOrCreateTag();
                tag.put(BLOCK_ENTITY_TAG, bet);
                // トップレベルにもコピー（クリエタブ等のフォールバック用）
                tag.putString("CutStateName", cut.getCutState().getBlock().toString());
                if (list.isEmpty()) {
                    list.add(stack);
                }
                return list;
            } else {
                // 空の場合はドロップなし（通常入手不能のため）
                // ただし superが返す空リストの場合は何もしない
                if (!list.isEmpty()) {
                    // 空のcut_blockはドロップしない（入手不能仕様）
                    list.clear();
                }
            }
        }
        // superが空を返す場合のフォールバック: 空なら何もドロップしない
        if (list.isEmpty()) {
            // 空のcut_blockはドロップなし
            return list;
        }
        return list;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (!be.isEmpty()) {
                CompoundTag bet = new CompoundTag();
                be.writeSyncData(bet);
                CompoundTag tag = stack.getOrCreateTag();
                tag.put(BLOCK_ENTITY_TAG, bet);
            }
        }
        return stack;
    }

    // ===== Rotation / Mirror =====

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }
}
