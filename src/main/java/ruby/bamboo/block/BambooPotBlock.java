package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.BambooPotBlockEntity;

/**
 * 竹鉢 (sakura BambooPot 移植・固定形状版)。
 * <p>
 * 方針変更: 後から追加要素なし。最初から固定形状 x16,y6(+フチ1),z8。
 * 鉢本体はモデルで固定描画、植物は BER で自由16本（シフト時はグリッド補助）。
 */
public class BambooPotBlock extends BaseEntityBlock {

    // ===== 調整用定数 =====
    /** 自由配置の基準スケール (花は1.5倍大きく、サボテンは小さく) */
    public static final float FREE_SCALE_BASE = 0.63f;
    public static final float FREE_SCALE_VARIATION = 0.06f;
    public static final float FREE_SCALE_CACTUS = 0.30f;
    /** 鉢上面Y */
    public static final float POT_TOP_Y = 0.30f;
    /** グリッド補助用（植物配置時のみ使用、土面中央線に寄せる） */
    public static final int GRID_DIVISIONS = 3;
    public static final float GRID_SCALE = 0.63f;
    public static final float GRID_SCALE_CACTUS = 0.30f;

    /** 固定形状 x16(0-16), y7(0-7: 本体6+フチ1), z8(4-12) - NORTH/SOUTH用 */
    public static final VoxelShape SHAPE_NS = box(0.0D, 0.0D, 4.0D, 16.0D, 7.0D, 12.0D);
    public static final VoxelShape SHAPE_EW = box(4.0D, 0.0D, 0.0D, 12.0D, 7.0D, 16.0D);
    /** 後方互換 */
    public static final VoxelShape SHAPE = SHAPE_NS;
    public static final VoxelShape SHAPE_COLLISION = SHAPE_NS;

    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BambooPotColor> COLOR = EnumProperty.create("color", BambooPotColor.class);

    public BambooPotBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .strength(0.5f)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(ATTACHED, false).setValue(FACING, Direction.NORTH).setValue(COLOR, BambooPotColor.BROWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ATTACHED, FACING, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState().setValue(FACING, facing).setValue(ATTACHED, false);
        return state;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction f = state.getValue(FACING);
        if (f == Direction.EAST || f == Direction.WEST) return SHAPE_EW;
        return SHAPE_NS;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BambooPotBlockEntity(pos, state);
    }

    // ===== 向き関連ヘルパー =====
    public static float[] worldToLocal(float wx, float wz, Direction facing) {
        return switch (facing) {
            case EAST -> new float[]{-wz, wx}; // local<-world for EAST
            case SOUTH -> new float[]{-wx, -wz};
            case WEST -> new float[]{wz, -wx};
            default -> new float[]{wx, wz}; // NORTH
        };
    }
    public static float[] localToWorld(float lx, float lz, Direction facing) {
        return switch (facing) {
            case EAST -> new float[]{lz, -lx};
            case SOUTH -> new float[]{-lx, -lz};
            case WEST -> new float[]{-lz, lx};
            default -> new float[]{lx, lz};
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }
        ItemStack handStack = player.getItemInHand(hand);
        // 染料で色変更（どの面からでも可能、後方互換なし）
        if (!handStack.isEmpty() && handStack.getItem() instanceof DyeItem dyeItem) {
            DyeColor dye = dyeItem.getDyeColor();
            BambooPotColor col = BambooPotColor.fromDye(dye);
            if (col != null && state.getValue(COLOR) != col) {
                if (level.isClientSide) return InteractionResult.sidedSuccess(true);
                level.setBlock(pos, state.setValue(COLOR, col), 3);
                if (!player.isCreative()) handStack.shrink(1);
                return InteractionResult.sidedSuccess(false);
            }
            return InteractionResult.FAIL;
        }
        boolean isShift = player.isShiftKeyDown();
        if (hit.getDirection() != Direction.UP) return InteractionResult.PASS;
        double hitX = Mth.clamp(hit.getLocation().x - pos.getX(), 0.0, 1.0);
        double hitZ = Mth.clamp(hit.getLocation().z - pos.getZ(), 0.0, 1.0);
        Direction facing = state.getValue(FACING);
        // ワールドoffset -> ローカルoffset（回転を考慮）
        float worldOffX = (float) hitX - 0.5f;
        float worldOffZ = (float) hitZ - 0.5f;
        float[] localHit = worldToLocal(worldOffX, worldOffZ, facing);
        float localHitX = localHit[0] + 0.5f;
        float localHitZ = localHit[1] + 0.5f;

        // 植物を植える
        if (!handStack.isEmpty() && isValidPlant(handStack)) {
            if (pot.getPlantCount() >= BambooPotBlockEntity.MAX_PLANTS) return InteractionResult.FAIL;
            boolean useGrid = isShift;
            if (useGrid) {
                if (pot.getGridCount() >= BambooPotBlockEntity.MAX_GRID) return InteractionResult.FAIL;
                // シフトは土面中央線(Z=0)に寄せ、長軸Xは2ドット(0.125)単位でフリー
                float rawLX = localHit[0];
                float offsetLX = Mth.clamp(Math.round(rawLX * 8f) / 8f, -0.375f, 0.375f);
                float offsetLZ = 0f; // 中央線
                boolean isCactus = handStack.is(Items.CACTUS);
                float scale = isCactus ? GRID_SCALE_CACTUS : GRID_SCALE;
                if (!level.isClientSide) scale += (level.random.nextFloat() - 0.5f) * 0.02f;
                if (level.isClientSide) return InteractionResult.sidedSuccess(true);
                boolean ok = pot.addPlant(handStack, offsetLX, offsetLZ, scale, true);
                if (ok) {
                    if (!player.isCreative()) handStack.shrink(1);
                    updateAttached(level, pos, state);
                    return InteractionResult.sidedSuccess(false);
                }
                return InteractionResult.FAIL;
            } else {
                float offsetLX = Mth.clamp(localHit[0], -0.40f, 0.40f);
                float offsetLZ = Mth.clamp(localHit[1], -0.18f, 0.18f);
                boolean isCactus = handStack.is(Items.CACTUS);
                float base = isCactus ? FREE_SCALE_CACTUS : FREE_SCALE_BASE;
                float scale = base + (level.random.nextFloat() - 0.5f) * FREE_SCALE_VARIATION;
                scale = Mth.clamp(scale, isCactus ? 0.28f : 0.55f, isCactus ? 0.32f : 0.65f);
                if (level.isClientSide) return InteractionResult.sidedSuccess(true);
                boolean ok = pot.addPlant(handStack, offsetLX, offsetLZ, scale, false);
                if (ok) {
                    if (!player.isCreative()) handStack.shrink(1);
                    updateAttached(level, pos, state);
                    return InteractionResult.sidedSuccess(false);
                }
                return InteractionResult.FAIL;
            }
        }

        // 空手で植物を取り出す（最も近い植物）— ローカル座標で距離比較
        if (handStack.isEmpty() && pot.getPlantCount() > 0) {
            if (level.isClientSide) return InteractionResult.sidedSuccess(true);
            // worldToLocal済みのlocalHitで最も近いローカル植物を探す
            float targetLX = localHit[0];
            float targetLZ = localHit[1];
            // pot.removeNearestはワールド基準だが、ローカルで探すため自前で探す
            var plants = pot.getPlants();
            int bestIdx = -1;
            double bestD2 = Double.MAX_VALUE;
            for (int i = 0; i < plants.size(); i++) {
                var e = plants.get(i);
                double dx = e.offsetX - targetLX;
                double dz = e.offsetZ - targetLZ;
                double d2 = dx * dx + dz * dz;
                if (d2 < bestD2) { bestD2 = d2; bestIdx = i; }
            }
            ItemStack taken = ItemStack.EMPTY;
            if (bestIdx >= 0) {
                // 直接取り出し（BEのremoveロジックをローカルで再現）
                taken = plants.get(bestIdx).stack.copy();
                plants.remove(bestIdx);
                pot.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            if (taken.isEmpty()) taken = pot.removeLast();
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
        if (!(level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot)) return;
        boolean hasPlant = pot.getPlantCount() > 0;
        boolean cur = old.getValue(ATTACHED);
        if (cur != hasPlant) {
            BlockState next = old.setValue(ATTACHED, hasPlant);
            level.setBlock(pos, next, 3);
        } else {
            level.sendBlockUpdated(pos, old, old, 3);
        }
    }

    /**
     * 鉢に挿せる植物か判定。鉢自体は除外（鉢は上記で別扱い）。
     */
    public static boolean isValidPlant(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (stack.is(ruby.bamboo.core.init.BambooBlocks.BAMBOO_POT.get().asItem())) return false; // 鉢は植物扱いしない
        } catch (Exception ignored) {}
        if (stack.is(ItemTags.SMALL_FLOWERS)) return true;
        if (stack.is(ItemTags.SAPLINGS)) return true;
        if (stack.is(Items.CACTUS)) return true;
        if (stack.is(Items.DEAD_BUSH)) return true;
        if (stack.is(Items.BROWN_MUSHROOM)) return true;
        if (stack.is(Items.RED_MUSHROOM)) return true;
        if (stack.is(Items.CRIMSON_FUNGUS)) return true;
        if (stack.is(Items.WARPED_FUNGUS)) return true;
        if (stack.is(Items.BAMBOO)) return true;
        if (stack.is(Items.FERN)) return true;
        if (stack.getItem() instanceof BlockItem bi) {
            var block = bi.getBlock();
            var holder = block.builtInRegistryHolder();
            if (holder.is(BlockTags.SMALL_FLOWERS)) return true;
            if (holder.is(BlockTags.SAPLINGS)) return true;
            if (holder.is(BlockTags.FLOWERS)) return true;
        }
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot) {
                pot.dropAllContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
