package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import ruby.bamboo.block.entity.FlowerBedBlockEntity;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 花壇 (プランターの1ブロック拡大版)。
 * <p>
 * プランター ({@link BambooPotBlock}) の植栽システムを流用し、フルキューブ化したもの。
 * 上面は {@code pot_top} テクスチャ、側面・底面はバニラ dirt。色・向きなし、{@code ATTACHED} のみ。
 * 破壊時は自身を落とさず土 + 植栽品を返却する (loot側で dirt 指定 + onRemoveで植栽ドロップ)。
 */
public class FlowerBedBlock extends BaseEntityBlock {

    /** 植栽ありで true (モデル切替用ではなくBER/更新通知用)。 */
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;

    /** 自由配置のクランプ (中心からのオフセット)。鉢 (-0.40/-0.18) より全幅に拡大。 */
    public static final float FREE_CLAMP = 0.42f;
    /** グリッド分割数 (4x4=16セル)。 */
    public static final int GRID_DIVISIONS = 4;
    /** グリッド配置のクランプ (4x4セル中心の最大値)。 */
    public static final float GRID_CLAMP = 0.375f;

    /**
     * ヒット位置 (ブロック内 0.0〜1.0) を 4x4 グリッドのセル中心にスナップする。
     * 中心は -0.375/-0.125/+0.125/+0.375。
     */
    public static float gridSnap(double hit) {
        int idx = Mth.clamp((int) Math.floor(hit * GRID_DIVISIONS), 0, GRID_DIVISIONS - 1);
        return idx * (1.0f / GRID_DIVISIONS) + (0.5f / GRID_DIVISIONS) - 0.5f;
    }

    public FlowerBedBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT)
                .sound(SoundType.GRAVEL)
                .strength(0.6F));
        this.registerDefaultState(this.stateDefinition.any().setValue(ATTACHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ATTACHED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlowerBedBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FlowerBedBlockEntity bed)) {
            return InteractionResult.PASS;
        }
        if (hit.getDirection() != Direction.UP) {
            return InteractionResult.PASS;
        }
        ItemStack handStack = player.getItemInHand(hand);
        boolean isShift = player.isShiftKeyDown();
        double hitX = Mth.clamp(hit.getLocation().x - pos.getX(), 0.0, 1.0);
        double hitZ = Mth.clamp(hit.getLocation().z - pos.getZ(), 0.0, 1.0);

        // 植物を植える
        if (!handStack.isEmpty() && isValidPlant(handStack)) {
            if (bed.getPlantCount() >= FlowerBedBlockEntity.MAX_PLANTS) {
                return InteractionResult.FAIL;
            }
            if (isShift && bed.getGridCount() >= FlowerBedBlockEntity.MAX_GRID) {
                return InteractionResult.FAIL;
            }
            if (level.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }
            float offsetX;
            float offsetZ;
            float scale;
            boolean isGrid = isShift;
            boolean isCactus = handStack.is(Items.CACTUS);
            if (isGrid) {
                // 4x4グリッドのセル中心にスナップ
                offsetX = gridSnap(hitX);
                offsetZ = gridSnap(hitZ);
                float base = isCactus ? BambooPotBlock.GRID_SCALE_CACTUS : BambooPotBlock.GRID_SCALE;
                scale = base + (level.random.nextFloat() - 0.5f) * 0.02f;
            } else {
                offsetX = Mth.clamp((float) hitX - 0.5f, -FREE_CLAMP, FREE_CLAMP);
                offsetZ = Mth.clamp((float) hitZ - 0.5f, -FREE_CLAMP, FREE_CLAMP);
                float base = isCactus ? BambooPotBlock.FREE_SCALE_CACTUS : BambooPotBlock.FREE_SCALE_BASE;
                scale = base + (level.random.nextFloat() - 0.5f) * BambooPotBlock.FREE_SCALE_VARIATION;
                scale = Mth.clamp(scale, isCactus ? 0.28f : 0.55f, isCactus ? 0.32f : 0.65f);
            }
            boolean ok = bed.addPlant(handStack, offsetX, offsetZ, scale, isGrid);
            if (ok) {
                if (!player.isCreative()) {
                    handStack.shrink(1);
                }
                updateAttached(level, pos, state);
                return InteractionResult.sidedSuccess(false);
            }
            return InteractionResult.FAIL;
        }

        // 空手で植物を取り出す (最も近い植物)
        if (handStack.isEmpty() && bed.getPlantCount() > 0) {
            if (level.isClientSide) {
                return InteractionResult.sidedSuccess(true);
            }
            float targetX = (float) hitX - 0.5f;
            float targetZ = (float) hitZ - 0.5f;
            var plants = bed.getPlants();
            int bestIdx = -1;
            double bestD2 = Double.MAX_VALUE;
            for (int i = 0; i < plants.size(); i++) {
                var e = plants.get(i);
                double dx = e.offsetX - targetX;
                double dz = e.offsetZ - targetZ;
                double d2 = dx * dx + dz * dz;
                if (d2 < bestD2) {
                    bestD2 = d2;
                    bestIdx = i;
                }
            }
            ItemStack taken = ItemStack.EMPTY;
            if (bestIdx >= 0) {
                taken = plants.get(bestIdx).stack.copy();
                plants.remove(bestIdx);
                bed.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            if (taken.isEmpty()) {
                taken = bed.removeLast();
            }
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, taken);
                }
                updateAttached(level, pos, state);
            }
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    private void updateAttached(Level level, BlockPos pos, BlockState old) {
        if (!(level.getBlockEntity(pos) instanceof FlowerBedBlockEntity bed)) {
            return;
        }
        boolean hasPlant = bed.getPlantCount() > 0;
        boolean cur = old.getValue(ATTACHED);
        if (cur != hasPlant) {
            level.setBlock(pos, old.setValue(ATTACHED, hasPlant), 3);
        } else {
            level.sendBlockUpdated(pos, old, old, 3);
        }
    }

    /**
     * 花壇に植えられる植物か判定。プランター判定を流用し、花壇・鉢自体は除外する。
     */
    public static boolean isValidPlant(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.is(BambooBlocks.FLOWER_BED.get().asItem())) {
                return false;
            }
        } catch (Exception ignored) {
        }
        return BambooPotBlock.isValidPlant(stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FlowerBedBlockEntity bed) {
                bed.dropAllContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
