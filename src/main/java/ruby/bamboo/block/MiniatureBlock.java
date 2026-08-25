package ruby.bamboo.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.MiniatureBlockEntity;
import ruby.bamboo.core.MiniatureWhitelist;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * ミニチュア (箱庭) ブロック — 1.20.1 移植版。
 * <p>
 * sakura 1.16.5 Miniature.java の移植。BaseEntityBlock + FACING + ENABLED。
 * 中身は MiniatureBlockEntity の cells[size][size][size] に BlockState を直保存。
 * 描画は BER (MiniatureBlockRenderer) で 1/size 縮小して tesselate。
 */
public class MiniatureBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    // 外枠薄板 (ENABLED=false時や境界). 旧 UP/DOWN/EAST etc 15.9/0.1
    public static final VoxelShape UP_AABB = Block.box(0, 15.9, 0, 16, 16, 16);
    public static final VoxelShape DOWN_AABB = Block.box(0, 0, 0, 16, 0.1, 16);
    public static final VoxelShape EAST_AABB = Block.box(15.9, 0, 0, 16, 16, 16);
    public static final VoxelShape NORTH_AABB = Block.box(0, 0, 0, 16, 16, 0.1);
    public static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15.9, 16, 16, 16);
    public static final VoxelShape WEST_AABB = Block.box(0, 0, 0, 0.1, 16, 16);
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    public MiniatureBlock() {
        super(Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f, 3.0f)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENABLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        boolean hasCells = false;
        if (stack.hasTag() && stack.getTag().contains(BLOCK_ENTITY_TAG)) {
            CompoundTag bet = stack.getTag().getCompound(BLOCK_ENTITY_TAG);
            if (bet.contains(MiniatureBlockEntity.TAG_CELLS) && bet.getList(MiniatureBlockEntity.TAG_CELLS, 10).size() > 0) {
                hasCells = true;
            }
            if (bet.contains(MiniatureBlockEntity.TAG_SIZE)) {
                hasCells = hasCells || !bet.getList(MiniatureBlockEntity.TAG_CELLS, 10).isEmpty();
            }
        }
        // 非空セルの有無で ENABLED を決めるが、配置直後は BE がまだ無いため一旦 false。
        // setPlacedBy で正しく更新する。
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ENABLED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            // Size 反映
            int size = MiniatureBlockEntity.getSizeFromStack(stack);
            be.setSize(size);
            // Cells 復元 (BlockEntityTag)
            if (stack.hasTag() && stack.getTag().contains(BLOCK_ENTITY_TAG)) {
                CompoundTag bet = stack.getTag().getCompound(BLOCK_ENTITY_TAG);
                // Size は既に適用済みだが、Cells を含む完全な bet を読み込む
                // setSizeでクリアされているため、bet の Cells を上書きする
                be.readSyncData(bet);
                // readSyncData は dirty を false にするので、設置直後は dirty 扱いにしておく
                // ただし同期を即時行いたい場合は markDirtyAndSync
                if (!be.isEmpty()) {
                    be.markDirtyAndSync();
                    // ENABLED 同期
                    level.setBlock(pos, state.setValue(ENABLED, true), 3);
                    be.rebuildShapeCache();
                }
            } else {
                // 空の場合は ENABLED false のまま
                be.rebuildShapeCache();
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
        return new MiniatureBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BambooBlockEntities.MINIATURE_BE.get(), MiniatureBlockEntity::tick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // BE破棄時は何もドロップしない (getDropsで NBT付き ItemStack を返す)
            // ただし level.removeBlockEntity は super で行われる
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    // ===== Drops / Pick =====

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> list = super.getDrops(state, builder);
        // LootContext から BE を取得して NBT を付与
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MiniatureBlockEntity mini) {
            if (!mini.isEmpty()) {
                // super.getDrops は通常 ItemStack(1)を返す。空なら生成
                ItemStack stack;
                if (list.isEmpty()) {
                    stack = new ItemStack(this);
                } else {
                    stack = list.get(0);
                }
                // Cells を BlockEntityTag に保存
                CompoundTag bet = new CompoundTag();
                mini.writeSyncData(bet);
                // Size はトップにも書く (クリエタブ互換)
                CompoundTag tag = stack.getOrCreateTag();
                tag.putInt(MiniatureBlockEntity.TAG_SIZE, mini.getSize());
                tag.put(BLOCK_ENTITY_TAG, bet);
                if (list.isEmpty()) {
                    list.add(stack);
                }
                // BE側の削除は onRemove で行われるので、ここでは何もしない
            } else {
                // 空の場合は Size のみ (設置時のサイズ保持)
                if (!list.isEmpty()) {
                    ItemStack stack = list.get(0);
                    if (be instanceof MiniatureBlockEntity mini2) {
                        CompoundTag tag = stack.getOrCreateTag();
                        if (!tag.contains(MiniatureBlockEntity.TAG_SIZE)) {
                            tag.putInt(MiniatureBlockEntity.TAG_SIZE, mini2.getSize());
                        }
                    }
                }
            }
        }
        // super が空を返す場合 (loot_table未定義) のフォールバック
        if (list.isEmpty()) {
            ItemStack stack = new ItemStack(this);
            BlockEntity be2 = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (be2 instanceof MiniatureBlockEntity mini) {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putInt(MiniatureBlockEntity.TAG_SIZE, mini.getSize());
                if (!mini.isEmpty()) {
                    CompoundTag bet = new CompoundTag();
                    mini.writeSyncData(bet);
                    tag.put(BLOCK_ENTITY_TAG, bet);
                }
            }
            list.add(stack);
        }
        return list;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        // ピックブロック: ミニチュア自身を返す (中身保持)。内部セルを指していればそのブロックを返す仕様もあるが、簡易ではミニチュアを返す。
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(MiniatureBlockEntity.TAG_SIZE, be.getSize());
            if (!be.isEmpty()) {
                CompoundTag bet = new CompoundTag();
                be.writeSyncData(bet);
                tag.put(BLOCK_ENTITY_TAG, bet);
            }
        }
        return stack;
    }

    // ===== Interaction =====

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MiniatureBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        int size = be.getSize();

        BlockPos targetPos = calcHitPos(pos, hit.getLocation(), hit.getDirection().getOpposite(), size);
        BlockPos hitPos = calcHitPos(pos, hit.getLocation(), hit.getDirection(), size);

        // 1) 手持ちがミニチュア自身 → コピー (NBT複製ドロップ)
        if (!held.isEmpty() && held.is(BambooBlocks.MINIATURE.get().asItem())) {
            if (!be.isEmpty()) {
                ItemStack copy = new ItemStack(BambooBlocks.MINIATURE.get());
                CompoundTag tag = copy.getOrCreateTag();
                tag.putInt(MiniatureBlockEntity.TAG_SIZE, be.getSize());
                CompoundTag bet = new CompoundTag();
                be.writeSyncData(bet);
                tag.put(BLOCK_ENTITY_TAG, bet);
                // スプリット: クリエ以外は1個消費してコピーをドロップ
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                // コピーをドロップ or インベントリへ
                if (!player.addItem(copy)) {
                    player.drop(copy, false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 2) インタラクショントグル (非TEかつ許可リスト) — 設置/除去より優先
        BlockState targetState = be.getCell(targetPos);
        if (!targetState.isAir() && MiniatureWhitelist.canInteract(targetState)) {
            BlockState toggled = MiniatureWhitelist.toggleInteractable(targetState);
            if (toggled != null) {
                be.setCell(targetPos, toggled);
                // validCheck不要、shape再構築
                be.rebuildShapeCache();
                level.sendBlockUpdated(pos, state, state, 3);
                return InteractionResult.SUCCESS;
            }
        }

        // 3) 手持ちが BlockItem → 配置
        if (!held.isEmpty() && held.getItem() instanceof BlockItem blockItem) {
            // 既にoccupiedなセルには置けない
            BlockPos placePos = null;
            // hitPos が空かつ範囲内ならそこへ、そうでなければ targetPos が空ならそこへ (簡易)
            if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && be.getCell(hitPos).isAir()) {
                placePos = hitPos;
            } else if (be.isInRange(targetPos.getX(), targetPos.getY(), targetPos.getZ()) && be.getCell(targetPos).isAir()) {
                // targetが空ならそこへ (壁に直接置くケース)
                placePos = targetPos;
            }
            if (placePos != null) {
                Block block = blockItem.getBlock();
                BlockState toPlace = block.defaultBlockState();
                // whitelist
                if (!MiniatureWhitelist.canPlace(toPlace)) {
                    return InteractionResult.FAIL;
                }
                // TE所持ブロックは見た目のみ許可 (そのまま置く)
                // 配置
                if (be.setCell(placePos, toPlace)) {
                    be.rebuildShapeCache();
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    // ENABLED 更新
                    BlockState newState = level.getBlockState(pos);
                    if (newState.is(this) && !newState.getValue(ENABLED)) {
                        level.setBlock(pos, newState.setValue(ENABLED, true), 3);
                    } else {
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.FAIL;
        }

        // 4) 素手 → 除去 + validCheck
        if (held.isEmpty()) {
            BlockState inner = be.getCell(targetPos);
            if (!inner.isAir()) {
                be.setCell(targetPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                validCheck(be, targetPos, player);
                be.rebuildShapeCache();
                // ENABLED 更新
                boolean empty = be.isEmpty();
                BlockState newState = level.getBlockState(pos);
                if (newState.is(this) && newState.getValue(ENABLED) != !empty) {
                    level.setBlock(pos, newState.setValue(ENABLED, !empty), 3);
                } else {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        return InteractionResult.PASS;
    }

    // 依存ブロックの落下チェック (ドア等の isValidPosition)
    private void validCheck(MiniatureBlockEntity be, BlockPos removedPos, Player player) {
        for (Direction dir : Direction.values()) {
            BlockPos n = removedPos.relative(dir);
            if (!be.isInRange(n.getX(), n.getY(), n.getZ())) {
                continue;
            }
            BlockState ns = be.getCell(n);
            if (ns.isAir()) {
                continue;
            }
            // 簡易: canSurvive 的なチェックを仮置き。Bamboo独自ブロック以外は常に有効とみなす。
            // 1.20.1の canSurvive は LevelReader を要するため、簡易ではドア等の2ブロック構造のみチェック
            // ドアなら下が空なら落下
            try {
                // 下が空ならドア等の上半分は落下させる簡易ルール
                if (ns.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    // DOUBLE_BLOCK_HALF が UPPER なら下をチェック
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf half = ns.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                    if (half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                        BlockPos below = n.below();
                        if (!be.isInRange(below.getX(), below.getY(), below.getZ()) || be.getCell(below).isAir()) {
                            be.setCell(n, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                            validCheck(be, n, player);
                        }
                    }
                }
            } catch (Exception e) {
                // 無視
            }
        }
    }

    // ===== HitPos計算 (旧 calcHitPos移植) =====

    public static BlockPos calcHitPos(BlockPos blockPos, Vec3 hitVec, Direction face, int size) {
        Vec3 rel = hitVec.subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        double cell = 1.0 / size;
        Vec3 off = rel.add(face.getStepX() * cell * 0.5, face.getStepY() * cell * 0.5, face.getStepZ() * cell * 0.5);
        int x = (int) Math.floor(off.x / cell);
        int y = (int) Math.floor(off.y / cell);
        int z = (int) Math.floor(off.z / cell);
        return new BlockPos(x, y, z);
    }

    // ===== Shape / Direction (AGENTS.md: public必須) =====

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!state.getValue(ENABLED)) {
            return getFaceShape(state.getValue(FACING));
        }
        // ENABLED=true かつ BEあり → セル合成
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            VoxelShape cached = be.getShapeCache();
            if (cached != null) {
                return cached;
            }
            // キャッシュが無ければ再構築 (サーバ側でも)
            if (!be.isEmpty()) {
                return be.rebuildShapeCache();
            }
            // 中身が空だが ENABLED=true の不整合時はフルブロック
            return Shapes.block();
        }
        return Shapes.block();
    }

    // 旧: 面の薄板 (15.9/0.1)
    private VoxelShape getFaceShape(Direction dir) {
        return switch (dir) {
            case DOWN -> DOWN_AABB;
            case UP -> UP_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
        };
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState newState = super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            // 隣接更新で再描画を促す (旧 notifyBlockUpdate 相当)
            be.setShapeCache(null);
            if (level instanceof Level lvl) {
                lvl.sendBlockUpdated(pos, newState, newState, 2);
            }
        }
        return newState;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
