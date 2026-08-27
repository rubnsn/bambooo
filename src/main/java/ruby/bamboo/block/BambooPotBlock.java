package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.BambooPotBlockEntity;

/**
 * 竹鉢 (sakura BambooPot 移植)。
 * <p>
 * sakura 1.16.5 版は empty TE だったが、本移植では鉢植え風機能を持たせる:
 * 1ブロック1鉢、右クリックで小植物(花/苗木/サボテン等)を出し入れ。
 */
public class BambooPotBlock extends BaseEntityBlock {

    public static final VoxelShape SHAPE = box(6.0D, 0.0D, 6.0D, 10.0D, 4.0D, 10.0D);
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;

    public BambooPotBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(0.5f)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(ATTACHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ATTACHED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BambooPotBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot) {
            ItemStack handStack = player.getItemInHand(hand);
            ItemStack stored = pot.getItem(0);
            if (!stored.isEmpty()) {
                // 取り出し
                if (!level.isClientSide) {
                    ItemStack taken = pot.removeItem(0, 1);
                    if (!player.isCreative()) {
                        if (!player.getInventory().add(taken)) {
                            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, taken);
                        }
                    } else {
                        // クリエイティブは手元に返さずドロップしない代わりに、BEだけ空にする
                        // ただし取り出した分は消えるので、再設置テスト用に手持ちに返さないのが sakura WallShelf と同様
                        // ここではクリエイティブでも手持ちに返さず、単にBEを空にする
                        // だが利便性のため、空の場合はドロップ無し
                    }
                    updateAttached(level, pos, state, true);
                    pot.setChanged();
                    level.sendBlockUpdated(pos, state, state.setValue(ATTACHED, false), 3);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                // 挿入: 手持ちが有効な植物か
                if (!handStack.isEmpty() && isValidPlant(handStack)) {
                    if (!level.isClientSide) {
                        ItemStack copy = handStack.copy();
                        copy.setCount(1);
                        pot.setItem(0, copy);
                        if (!player.isCreative()) {
                            handStack.shrink(1);
                        }
                        updateAttached(level, pos, state, false);
                        pot.setChanged();
                        level.sendBlockUpdated(pos, state, state.setValue(ATTACHED, true), 3);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
        return InteractionResult.PASS;
    }

    private void updateAttached(Level level, BlockPos pos, BlockState old, boolean wasAttached) {
        // ATTACHED を植物有無に同期。存否チェックはBE側だがここで明示的に更新
        BlockState next = old.setValue(ATTACHED, !wasAttached ? true : false);
        // 値が変わる場合のみ setBlock（チラつき防止）。ただし pot.setItem 後の同期は use 内で送るのでここでは最小限
        if (old.getValue(ATTACHED) != next.getValue(ATTACHED)) {
            level.setBlock(pos, next, 3);
        }
    }

    /**
     * 鉢に挿せる植物か判定。
     * タグ small_flowers / saplings、またはサボテン/キノコ/枯れ木/竹等を許可。
     */
    public static boolean isValidPlant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // タグ判定 (ItemTags)
        if (stack.is(ItemTags.SMALL_FLOWERS)) return true;
        if (stack.is(ItemTags.SAPLINGS)) return true;
        // 個別アイテム: cactus, mushrooms, dead bush, bamboo, fern, etc.
        if (stack.is(Items.CACTUS)) return true;
        if (stack.is(Items.DEAD_BUSH)) return true;
        if (stack.is(Items.BROWN_MUSHROOM)) return true;
        if (stack.is(Items.RED_MUSHROOM)) return true;
        if (stack.is(Items.CRIMSON_FUNGUS)) return true;
        if (stack.is(Items.WARPED_FUNGUS)) return true;
        if (stack.is(Items.BAMBOO)) return true;
        if (stack.is(Items.FERN)) return true;
        // BlockItem として small_flowers 以外の花(例: sunflower 等は除外)
        // 追加: small_flowers タグに含まれないが鉢に飾れるものとして lily_pad, seagrass は除外
        // BlockTags で補足 (BlockItem の場合)
        if (stack.getItem() instanceof BlockItem bi) {
            var block = bi.getBlock();
            var holder = block.builtInRegistryHolder();
            if (holder.is(BlockTags.SMALL_FLOWERS)) return true;
            if (holder.is(BlockTags.SAPLINGS)) return true;
            if (holder.is(BlockTags.FLOWERS)) return true; // 大きめの花も許可（任意）
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot) {
                Containers.dropContents(level, pos, pot);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
