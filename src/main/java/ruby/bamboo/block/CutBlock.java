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
 * 中身は CutBlockEntity の cutState + x/y/zLevel で管理。
 * 描画は INVISIBLE + BER で AABBに合わせたQuad再生成。
 * 空の場合は透明ダミー (Shapes.empty)。
 * FACINGは回転互換のため残置するが、Boundsは3軸絶対。
 * 回転はバニラ依存（水平のみ）: Block.rotate/mirror + CutBlockEntity#setBlockState でBoundsも回転。
 */
public class CutBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    // バニラ回転のBE連携用 pending（rotateはBlockStateのみでLevel/Posを持たないためThreadLocalでBEへ伝播）
    private static final ThreadLocal<Rotation> PENDING_ROTATION = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<Mirror> PENDING_MIRROR = ThreadLocal.withInitial(() -> null);

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
            java.util.List<CutBlockEntity.CutEntry> entries = CutBlockEntity.readEntriesFromStack(stack);
            if (!entries.isEmpty()) {
                // 複数Entriesを持つhoe回収品はそのまま復元
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains(BLOCK_ENTITY_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    CompoundTag bet = tag.getCompound(BLOCK_ENTITY_TAG);
                    be.readSyncData(bet);
                    be.invalidateShapeCache();
                    level.sendBlockUpdated(pos, state, state, 3);
                } else {
                    // フォールバック: 手動追加
                    be.clearEntries();
                    for (CutBlockEntity.CutEntry e : entries) {
                        be.addEntry(e.state, e.bounds);
                    }
                    level.sendBlockUpdated(pos, state, state, 3);
                }
                return;
            }
            CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
            // 空のcut_blockは論理的に存在しないため、空ならブロック自体を除去してFAIL相当にする
            if (data.state().isAir()) {
                // バニラ経由で置かれた空BEを即時除去し、空タグ送出を防ぐ
                if (!level.isClientSide) {
                    level.removeBlock(pos, false);
                }
                return;
            }
            be.setCutState(data.state());
            be.setLevels(data.xLevel(), data.yLevel(), data.zLevel());
            be.invalidateShapeCache();
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
            // 3軸絶対: FACINGに依存しない
            return be.getShapeCacheAbsolute();
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (be.isEmpty()) return true;
            if (!be.getEntries().isEmpty()) {
                boolean coversTop = false;
                for (var e : be.getEntries()) {
                    if (e.bounds[4] == 16) {
                        coversTop = true;
                        break;
                    }
                }
                return !coversTop;
            }
            return be.getYSize() < 16;
        }
        return false;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (be.isEmpty()) return 0;
            if (!be.getEntries().isEmpty()) {
                return 0;
            }
            if (be.getXSize() < 16 || be.getYSize() < 16 || be.getZSize() < 16) return 0;
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
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof CutBlockEntity cut) {
            if (!cut.isEmpty()) {
                if (!cut.getEntries().isEmpty()) {
                    List<ItemStack> result = new java.util.ArrayList<>();
                    for (CutBlockEntity.CutEntry e : cut.getEntries()) {
                        int xSize = e.bounds[3] - e.bounds[0];
                        int ySize = e.bounds[4] - e.bounds[1];
                        int zSize = e.bounds[5] - e.bounds[2];
                        byte xl = CutBlockEntity.sizeToLevel(xSize);
                        byte yl = CutBlockEntity.sizeToLevel(ySize);
                        byte zl = CutBlockEntity.sizeToLevel(zSize);
                        ItemStack s = ruby.bamboo.crafting.CutBlockRecipe.createCutBlockStack(e.state, xl, yl, zl);
                        result.add(s);
                    }
                    return result;
                }
                List<ItemStack> list = super.getDrops(state, builder);
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
                if (list.isEmpty()) {
                    list.add(stack);
                }
                return list;
            } else {
                List<ItemStack> list = super.getDrops(state, builder);
                if (!list.isEmpty()) {
                    list.clear();
                }
                return list;
            }
        }
        List<ItemStack> list = super.getDrops(state, builder);
        if (list.isEmpty()) {
            return list;
        }
        return list;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (!be.isEmpty() && !be.getEntries().isEmpty()) {
                // サーバセーフ: 先頭エントリを同等のcut_blockとして返す。BoundsではなくCutState+X/Y/ZLevelでスタック可能に
                CutBlockEntity.CutEntry first = be.getEntries().get(0);
                int xSize = first.bounds[3] - first.bounds[0];
                int ySize = first.bounds[4] - first.bounds[1];
                int zSize = first.bounds[5] - first.bounds[2];
                byte xl = CutBlockEntity.sizeToLevel(xSize);
                byte yl = CutBlockEntity.sizeToLevel(ySize);
                byte zl = CutBlockEntity.sizeToLevel(zSize);
                return ruby.bamboo.crafting.CutBlockRecipe.createCutBlockStack(first.state, xl, yl, zl);
            }
            if (!be.isEmpty()) {
                ItemStack stack = new ItemStack(this);
                CompoundTag bet = new CompoundTag();
                be.writeSyncData(bet);
                CompoundTag tag = stack.getOrCreateTag();
                tag.put(BLOCK_ENTITY_TAG, bet);
                return stack;
            }
        }
        return new ItemStack(this);
    }

    // ===== Rotation / Mirror (バニラ依存・水平のみ) =====

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        if (rot == Rotation.NONE) return state;
        PENDING_ROTATION.set(rot);
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        PENDING_MIRROR.set(mirror);
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    public static Rotation consumePendingRotation() {
        Rotation r = PENDING_ROTATION.get();
        PENDING_ROTATION.remove();
        return r;
    }

    public static Mirror consumePendingMirror() {
        Mirror m = PENDING_MIRROR.get();
        PENDING_MIRROR.remove();
        return m;
    }

    // ===== 細かい除去: ミニチュア準拠の左クリック/破壊進行度/WillDestroy =====

    static final ThreadLocal<Boolean> ALLOW_CUT_REMOVAL = ThreadLocal.withInitial(() -> false);

    public static boolean isRemovalAllowed() {
        return Boolean.TRUE.equals(ALLOW_CUT_REMOVAL.get());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (!be.isEmpty() && !be.getEntries().isEmpty()) {
                if (Boolean.TRUE.equals(ALLOW_CUT_REMOVAL.get())) {
                    return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
                }
                return false;
            }
            if (!be.isEmpty()) {
                if (Boolean.TRUE.equals(ALLOW_CUT_REMOVAL.get())) {
                    return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
                }
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pos) instanceof CutBlockEntity be)) {
            super.attack(state, level, pos, player);
            return;
        }
        if (be.isEmpty()) {
            super.attack(state, level, pos, player);
            return;
        }
        net.minecraft.world.phys.BlockHitResult hit = getHitForPlayer(player, level, pos);
        if (hit == null || !hit.getBlockPos().equals(pos)) return;
        CutBlockEntity.CutEntry target = findHitEntry(be, pos, hit);
        if (target == null) return;
        if (player.isCreative()) {
            breakInnerForAttack(be, target, level, pos, player, false);
        } else {
            return;
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter blockGetter, BlockPos pos) {
        if (blockGetter.getBlockEntity(pos) instanceof CutBlockEntity be) {
            if (!be.isEmpty()) {
                if (blockGetter instanceof Level level) {
                    net.minecraft.world.phys.BlockHitResult hit = getHitForPlayer(player, level, pos);
                    if (hit != null && hit.getBlockPos().equals(pos)) {
                        CutBlockEntity.CutEntry target = findHitEntry(be, pos, hit);
                        if (target != null) {
                            // ツールによらず葉っぱ相当の固定硬度 (誤破壊防止)。ツール・エンチャ無視で20tick
                            return 0.05f;
                        }
                    }
                    // 空隙を叩いた場合は外枠を壊さない
                    return 0.0f;
                }
            }
        }
        return super.getDestroyProgress(state, player, blockGetter, pos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (!(level.getBlockEntity(pos) instanceof CutBlockEntity be)) {
            super.playerWillDestroy(level, pos, state, player);
            return;
        }
        if (be.isEmpty()) {
            super.playerWillDestroy(level, pos, state, player);
            return;
        }
        // 中身ありは外枠を壊さず、内部破壊は BreakEvent に一本化 (二重ドロップ防止)
        // クライアントでは予測削除を抑止するため何もしない
        if (level.isClientSide) return;
        return;
    }

    public net.minecraft.world.phys.BlockHitResult getHitForPlayer(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
        return getHitForPlayerStatic(player, level, pos);
    }

    public static net.minecraft.world.phys.BlockHitResult getHitForPlayerStatic(net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
        try {
            double reach = player.isCreative() ? 5.0 : 4.5;
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1.0f);
            net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
            net.minecraft.world.phys.Vec3 end = eye.add(look.scale(reach));
            return level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        } catch (Exception e) {
            return null;
        }
    }

    public static CutBlockEntity.CutEntry findHitEntryStatic(CutBlockEntity be, BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
        return findHitEntry(be, pos, hit);
    }

    private CutBlockEntity.CutEntry findHitEntry(CutBlockEntity be, BlockPos pos, net.minecraft.world.phys.Vec3 hitVec) {
        return findHitEntry(be, pos, new net.minecraft.world.phys.BlockHitResult(hitVec, net.minecraft.core.Direction.UP, pos, false));
    }

    public static CutBlockEntity.CutEntry findHitEntry(CutBlockEntity be, BlockPos pos, net.minecraft.world.phys.BlockHitResult hit) {
        net.minecraft.world.phys.Vec3 hitVec = hit.getLocation();
        net.minecraft.core.Direction face = hit.getDirection();
        double hx = (hitVec.x - pos.getX()) * 16;
        double hy = (hitVec.y - pos.getY()) * 16;
        double hz = (hitVec.z - pos.getZ()) * 16;
        double eps = 0.01;
        for (CutBlockEntity.CutEntry e : be.getEntries()) {
            int[] b = e.bounds;
            boolean onFace = false;
            if (face == Direction.UP && Math.abs(hy - b[4]) < eps && hx >= b[0] - eps && hx <= b[3] + eps && hz >= b[2] - eps && hz <= b[5] + eps) onFace = true;
            else if (face == Direction.DOWN && Math.abs(hy - b[1]) < eps && hx >= b[0] - eps && hx <= b[3] + eps && hz >= b[2] - eps && hz <= b[5] + eps) onFace = true;
            else if (face == Direction.NORTH && Math.abs(hz - b[2]) < eps && hx >= b[0] - eps && hx <= b[3] + eps && hy >= b[1] - eps && hy <= b[4] + eps) onFace = true;
            else if (face == Direction.SOUTH && Math.abs(hz - b[5]) < eps && hx >= b[0] - eps && hx <= b[3] + eps && hy >= b[1] - eps && hy <= b[4] + eps) onFace = true;
            else if (face == Direction.WEST && Math.abs(hx - b[0]) < eps && hy >= b[1] - eps && hy <= b[4] + eps && hz >= b[2] - eps && hz <= b[5] + eps) onFace = true;
            else if (face == Direction.EAST && Math.abs(hx - b[3]) < eps && hy >= b[1] - eps && hy <= b[4] + eps && hz >= b[2] - eps && hz <= b[5] + eps) onFace = true;
            if (onFace) return e;
        }
        for (CutBlockEntity.CutEntry e : be.getEntries()) {
            int[] b = e.bounds;
            if (hx >= b[0] - eps && hx <= b[3] + eps && hy >= b[1] - eps && hy <= b[4] + eps && hz >= b[2] - eps && hz <= b[5] + eps) {
                if (hx > b[0] - eps && hx < b[3] + eps && hy > b[1] - eps && hy < b[4] + eps && hz > b[2] - eps && hz < b[5] + eps) {
                    return e;
                }
            }
        }
        if (be.getEntries().isEmpty() && !be.isEmpty()) {
            int[] b = be.getBoundsAbsolute();
            boolean onFace = false;
            if (face == Direction.UP && Math.abs(hy - b[4]) < 0.5) onFace = true;
            else if (face == Direction.DOWN && Math.abs(hy - b[1]) < 0.5) onFace = true;
            else if (face == Direction.NORTH && Math.abs(hz - b[2]) < 0.5) onFace = true;
            else if (face == Direction.SOUTH && Math.abs(hz - b[5]) < 0.5) onFace = true;
            else if (face == Direction.WEST && Math.abs(hx - b[0]) < 0.5) onFace = true;
            else if (face == Direction.EAST && Math.abs(hx - b[3]) < 0.5) onFace = true;
            if (onFace || (hx >= b[0] && hx <= b[3] && hy >= b[1] && hy <= b[4] && hz >= b[2] && hz <= b[5])) {
                return new CutBlockEntity.CutEntry(be.getCutState(), b);
            }
        }
        return null;
    }

    public void breakInnerForAttack(CutBlockEntity be, CutBlockEntity.CutEntry target, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, boolean drop) {
        BlockState inner = target.state;
        if (inner == null || inner.isAir()) return;
        CutBlockEntity.CutEntry toRemove = null;
        for (CutBlockEntity.CutEntry e : be.getEntries()) {
            if (e == target) { toRemove = e; break; }
            if (java.util.Arrays.equals(e.bounds, target.bounds) && e.state.equals(target.state)) { toRemove = e; break; }
        }
        boolean isOldSingle = be.getEntries().isEmpty() && !be.isEmpty();
        if (toRemove == null && isOldSingle) {
            toRemove = target;
        }
        if (drop && !level.isClientSide) {
            // 同等のカットブロックを返す (8x8x8土→8x8x8土のcut_block)。原料は返さず、同じNBTならスタックする
            int xS = target.bounds[3] - target.bounds[0];
            int yS = target.bounds[4] - target.bounds[1];
            int zS = target.bounds[5] - target.bounds[2];
            byte xl = CutBlockEntity.sizeToLevel(xS);
            byte yl = CutBlockEntity.sizeToLevel(yS);
            byte zl = CutBlockEntity.sizeToLevel(zS);
            ItemStack cutStack = ruby.bamboo.crafting.CutBlockRecipe.createCutBlockStack(target.state, xl, yl, zl);
            Block.popResource(level, pos, cutStack);
            try { player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this)); } catch (Exception e) {}
            ItemStack held = player.getMainHandItem();
            if (!player.isCreative() && !held.isEmpty() && held.isDamageableItem()) {
                try { held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND)); } catch (Exception e) {}
            }
            try { player.causeFoodExhaustion(0.005f); } catch (Exception e) {}
        }
        if (isOldSingle) {
            ALLOW_CUT_REMOVAL.set(true);
            try {
                level.removeBlock(pos, false);
            } finally {
                ALLOW_CUT_REMOVAL.set(false);
            }
        } else if (toRemove != null) {
            be.removeEntry(toRemove);
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
            if (be.isEmpty()) {
                ALLOW_CUT_REMOVAL.set(true);
                try {
                    level.removeBlock(pos, false);
                } finally {
                    ALLOW_CUT_REMOVAL.set(false);
                }
            }
        }
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CutBlockEntity be) {
            ItemStack held = player.getItemInHand(hand);
            if (!held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.HoeItem) {
                if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
                ItemStack stack = new ItemStack(this);
                CompoundTag tag = stack.getOrCreateTag();
                if (!be.isEmpty()) {
                    if (!be.getEntries().isEmpty()) {
                        CompoundTag bet = new CompoundTag();
                        be.writeSyncData(bet);
                        tag.put(BLOCK_ENTITY_TAG, bet);
                    } else {
                        CompoundTag bet = new CompoundTag();
                        be.writeSyncData(bet);
                        tag.put(BLOCK_ENTITY_TAG, bet);
                    }
                }
                if (!player.addItem(stack)) {
                    Block.popResource(level, pos, stack);
                }
                ALLOW_CUT_REMOVAL.set(true);
                try {
                    level.removeBlock(pos, false);
                } finally {
                    ALLOW_CUT_REMOVAL.set(false);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }
}
