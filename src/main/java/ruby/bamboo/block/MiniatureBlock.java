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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
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
import ruby.bamboo.block.entity.MiniatureFakeLevelReader;
import ruby.bamboo.core.MiniatureWhitelist;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * ミニチュア (箱庭) ブロック — 1.20.1 移植版。
 * <p>
 * sakura 1.16.5 Miniature.java の移植。BaseEntityBlock + FACING(6面) + ENABLED。
 * 中身は MiniatureBlockEntity の cells[size][size][size] に BlockState を直保存。
 * 描画: ENABLED=false時は薄板モデル(面考慮)、ENABLED=true時は INVISIBLE + BERで 1/size 縮小 tesselate。
 * アイテム/ブロック初期表示は旧仕様通り item/miniature.png を使用 (sakura item/generated)。
 */
public class MiniatureBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    // 外枠薄板 (ENABLED=false時や境界). 旧 UP/DOWN/EAST etc 15.9/0.1
    public static final VoxelShape UP_AABB = Block.box(0, 15.9, 0, 16, 16, 16);
    public static final VoxelShape DOWN_AABB = Block.box(0, 0, 0, 16, 0.1, 16);
    public static final VoxelShape EAST_AABB = Block.box(15.9, 0, 0, 16, 16, 16);
    public static final VoxelShape NORTH_AABB = Block.box(0, 0, 0, 16, 16, 0.1);
    public static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15.9, 16, 16, 16);
    public static final VoxelShape WEST_AABB = Block.box(0, 0, 0, 0.1, 16, 16);
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final ThreadLocal<Boolean> ALLOW_HOE_REMOVAL = ThreadLocal.withInitial(() -> false);

    public MiniatureBlock() {
        super(Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0f, 3.0f)
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false)
                .isRedstoneConductor((s, l, p) -> false)
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENABLED, false));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 面を考慮: クリック面の反対を FACING とする (sakura getStateForPlacement: context.getFace().getOpposite())
        // 旧 HORIZONTAL_FACING 方式では上面クリックが無視されていたため、全方向対応に修正。
        Direction face = context.getClickedFace();
        // BlockEntityTag の有無で ENABLED 判定 (setPlacedBy で最終確定)
        boolean hasCells = false;
        ItemStack stack = context.getItemInHand();
        if (stack.hasTag() && stack.getTag().contains(BLOCK_ENTITY_TAG)) {
            CompoundTag bet = stack.getTag().getCompound(BLOCK_ENTITY_TAG);
            if (bet.contains(MiniatureBlockEntity.TAG_CELLS) && bet.getList(MiniatureBlockEntity.TAG_CELLS, 10).size() > 0) {
                hasCells = true;
            }
            if (bet.contains(MiniatureBlockEntity.TAG_SIZE)) {
                hasCells = hasCells || !bet.getList(MiniatureBlockEntity.TAG_CELLS, 10).isEmpty();
            }
        }
        // 配置直後は BE 未生成のため一旦 false、setPlacedBy で正しく更新する
        // ただし NBT に Cells があれば最初から ENABLED=true にしておくと blockstate 初期ずれが減る
        return this.defaultBlockState()
                .setValue(FACING, face.getOpposite())
                .setValue(ENABLED, hasCells);
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
        // 中身なし(薄板)はモデル描画、中身ありは BER で縮小描画
        return state.getValue(ENABLED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
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
        // 外枠の破壊は BlockEvent.BreakEvent / onDestroyedByPlayer で汎用的に抑止 (中身ありは絶対に壊れない)。
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            if (!be.isEmpty()) {
                // クワ右クリック経由の許可フラグがある場合のみ外枠破壊を許可
                if (Boolean.TRUE.equals(ALLOW_HOE_REMOVAL.get())) {
                    return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
                }
                // 中身ありはクライアント予測含め外枠破壊を抑止 (MultiPlayerGameMode の予測除去を防ぐ)
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    // ===== 左クリック (attack) — ミニチュア内部破壊。外枠は BreakEvent で絶対保護。 =====
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof MiniatureBlockEntity be)) {
            super.attack(state, level, pos, player);
            return;
        }
        if (be.isEmpty()) {
            // 空なら通常の外枠破壊を許可
            super.attack(state, level, pos, player);
            return;
        }
        // 中身ありは外枠を絶対に壊さない (クワ右クリック以外)。左クリックは内部破壊のみ。
        if (player.isCreative()) {
            BlockHitResult hit = getHitForPlayer(player, level, pos);
            if (hit == null || !hit.getBlockPos().equals(pos)) {
                return;
            }
            BlockPos targetPos = calcHitPos(pos, hit.getLocation(), hit.getDirection().getOpposite(), be.getSize());
            BlockState inner = be.getCell(targetPos);
            if (inner.isAir()) {
                BlockPos hitPos = calcHitPos(pos, hit.getLocation(), hit.getDirection(), be.getSize());
                if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && !be.getCell(hitPos).isAir()) {
                    targetPos = hitPos;
                    inner = be.getCell(targetPos);
                } else {
                    return;
                }
            }
            // クリエは即時、ドロップなし。ツール耐久消費なし。
            breakInnerForAttack(be, targetPos, level, pos, player, false);
        } else {
            // サバイバルの単発クリックは getDestroyProgress / playerWillDestroy に委譲するため何もしない
            // outer の attack サウンドを抑止するため super を呼ばない
            return;
        }
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter blockGetter, BlockPos pos) {
        if (blockGetter.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            if (!be.isEmpty()) {
                // 中身ありは外枠を破壊させず、内側の硬度を返す (バニラ準拠の内部破壊)。
                // BreakEvent で外枠破壊はキャンセルされるため、ここで内側の進行度を返すことで
                // クライアントのクラックとサーバの破壊タイマーが内側に同期する。
                if (blockGetter instanceof Level level) {
                    BlockHitResult hit = getHitForPlayer(player, level, pos);
                    if (hit != null && hit.getBlockPos().equals(pos)) {
                        BlockPos targetPos = calcHitPos(pos, hit.getLocation(), hit.getDirection().getOpposite(), be.getSize());
                        BlockState inner = be.getCell(targetPos);
                        if (inner.isAir()) {
                            BlockPos hitPos = calcHitPos(pos, hit.getLocation(), hit.getDirection(), be.getSize());
                            if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && !be.getCell(hitPos).isAir()) {
                                targetPos = hitPos;
                                inner = be.getCell(targetPos);
                            }
                        }
                        if (!inner.isAir()) {
                            return inner.getDestroyProgress(player, blockGetter, pos);
                        }
                    }
                    // 空セルを叩いた場合は外枠を壊さない (0 = 進行なし)
                    return 0.0f;
                }
            }
        }
        return super.getDestroyProgress(state, player, blockGetter, pos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level.getBlockEntity(pos) instanceof MiniatureBlockEntity be)) {
            super.playerWillDestroy(level, pos, state, player);
            return;
        }
        if (be.isEmpty()) {
            // 空なら通常の外枠破壊 (ドロップは BlockEntityTag 無し)
            super.playerWillDestroy(level, pos, state, player);
            return;
        }
        // 中身ありは外枠を絶対に壊さない (BreakEvent でキャンセル)。ここでは内部をバニラ準拠で破壊。
        if (level.isClientSide) {
            // クライアントではサーバ同期に任せる (外枠の予測削除を抑止)
            return;
        }
        BlockHitResult hit = getHitForPlayer(player, level, pos);
        if (hit == null || !hit.getBlockPos().equals(pos)) {
            // ヒット不明なら内部破壊せず外枠も壊さない (BreakEvent でキャンセルされる)
            return;
        }
        BlockPos targetPos = calcHitPos(pos, hit.getLocation(), hit.getDirection().getOpposite(), be.getSize());
        BlockState inner = be.getCell(targetPos);
        if (inner.isAir()) {
            BlockPos hitPos = calcHitPos(pos, hit.getLocation(), hit.getDirection(), be.getSize());
            if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && !be.getCell(hitPos).isAir()) {
                targetPos = hitPos;
                inner = be.getCell(targetPos);
            } else {
                return;
            }
        }
        boolean isCreative = player.isCreative();
        // サバイバルならバニラ準拠のドロップ＆ツール耐久消費、クリエならドロップなし。
        breakInnerForAttack(be, targetPos, level, pos, player, !isCreative);
        // 外枠の playerWillDestroy(setBlock AIR) は呼ばない。BreakEvent で外枠破壊はキャンセルされる。
    }

    private BlockHitResult getHitForPlayer(Player player, Level level, BlockPos pos) {
        return getHitForPlayerStatic(player, level, pos);
    }

    public static BlockHitResult getHitForPlayerStatic(Player player, Level level, BlockPos pos) {
        try {
            double reach = player.isCreative() ? 5.0 : 4.5;
            Vec3 eye = player.getEyePosition(1.0f);
            Vec3 look = player.getViewVector(1.0f);
            Vec3 end = eye.add(look.scale(reach));
            return level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        } catch (Exception e) {
            return null;
        }
    }

    public static BlockPos calcHitPosStatic(BlockPos pos, Vec3 hitVec, Direction face, int size) {
        Vec3 rel = hitVec.subtract(Vec3.atLowerCornerOf(pos));
        Vec3 off = rel.add(face.getStepX() * (1.0 / size) * 0.5, face.getStepY() * (1.0 / size) * 0.5, face.getStepZ() * (1.0 / size) * 0.5);
        double cell = 1.0 / size;
        return new BlockPos((int) Math.floor(off.x / cell), (int) Math.floor(off.y / cell), (int) Math.floor(off.z / cell));
    }

    public void breakInnerForAttack(MiniatureBlockEntity be, BlockPos targetPos, Level level, BlockPos bePos,
            Player player, boolean drop) {
        BlockState inner = be.getCell(targetPos);
        if (inner.isAir()) {
            return;
        }
        // 二重占有の相方も考慮
        BlockPos otherPos = null;
        BlockState otherState = null;
        if (inner.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = inner.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            BlockPos other = half == DoubleBlockHalf.LOWER ? targetPos.above() : targetPos.below();
            if (be.isInRange(other.getX(), other.getY(), other.getZ())) {
                BlockState os = be.getCell(other);
                if (!os.isAir() && os.is(inner.getBlock()) && os.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && os.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half) {
                    otherPos = other;
                    otherState = os;
                }
            }
        } else if (inner.hasProperty(BlockStateProperties.BED_PART)) {
            try {
                BedPart part = inner.getValue(BlockStateProperties.BED_PART);
                Direction facing = inner.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                        ? inner.getValue(BlockStateProperties.HORIZONTAL_FACING)
                        : Direction.NORTH;
                BlockPos other = part == BedPart.FOOT ? targetPos.relative(facing) : targetPos.relative(facing.getOpposite());
                if (be.isInRange(other.getX(), other.getY(), other.getZ())) {
                    BlockState os = be.getCell(other);
                    if (!os.isAir() && os.is(inner.getBlock()) && os.hasProperty(BlockStateProperties.BED_PART)
                            && os.getValue(BlockStateProperties.BED_PART) != part) {
                        otherPos = other;
                        otherState = os;
                    }
                }
            } catch (Exception e) {
            }
        }
        ItemStack held = player.getMainHandItem();
        if (drop && level instanceof net.minecraft.server.level.ServerLevel slevel) {
            // バニラ準拠: ドロップ + 経験値 + 統計 + ツール耐久
            var drops = Block.getDrops(inner, slevel, bePos, be, player, held);
            for (ItemStack st : drops) {
                Block.popResource(level, bePos, st);
            }
            if (otherPos != null && otherState != null) {
                var drops2 = Block.getDrops(otherState, slevel, bePos, be, player, held);
                for (ItemStack st : drops2) {
                    Block.popResource(level, bePos, st);
                }
            }
            // 経験値 (鉱石等) — 1.20.1 では Block#getExpDrop のシグネが異なるため、経験値は popExperience 経由で
            // 必要なら後で対応。現状は try-catch で握りつぶし (ドロップ自体は getDrops で完結)。
            // 統計
            try {
                player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(inner.getBlock()));
                if (otherState != null) player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(otherState.getBlock()));
            } catch (Exception e) {}
            // ツール耐久 (バニラ準拠: 正しいツールでなくても消耗するものは消耗)
            if (!player.isCreative() && !held.isEmpty() && held.isDamageableItem()) {
                try {
                    held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
                } catch (Exception e) {}
            }
            // サウンド/パーティクル — ミニチュア内では通常パーティクル(2001)はサイズに対して大きすぎるため抑止。
            // 必要なら後で 1/size スケールのカスタムパーティクルに置換を検討。
            try {
                player.causeFoodExhaustion(0.005f);
            } catch (Exception e) {}
        } else if (!drop) {
            // クリエ: パーティクル抑止 (同上)
        } else {
            // drop==false でもクリエ以外で呼ばれない
        }
        be.setCell(targetPos, Blocks.AIR.defaultBlockState());
        if (otherPos != null) {
            be.setCell(otherPos, Blocks.AIR.defaultBlockState());
        }
        // 支持を失った周辺ブロックの落下・接続更新
        validCheck(be, targetPos, player);
        if (otherPos != null) {
            validCheck(be, otherPos, player);
        }
        // フェンス/レッドストーン等の接続更新
        for (Direction dir : Direction.values()) {
            BlockPos n = targetPos.relative(dir);
            if (be.isInRange(n.getX(), n.getY(), n.getZ()) && !be.getCell(n).isAir()) {
                updateInnerConnections(n, be, level, bePos);
            }
        }
        if (otherPos != null) {
            for (Direction dir : Direction.values()) {
                BlockPos n = otherPos.relative(dir);
                if (be.isInRange(n.getX(), n.getY(), n.getZ()) && !be.getCell(n).isAir()) {
                    updateInnerConnections(n, be, level, bePos);
                }
            }
        }
        be.rebuildShapeCache();
        // ENABLED 更新 (空になったら薄板に戻す)
        boolean empty = be.isEmpty();
        BlockState outer = level.getBlockState(bePos);
        if (outer.is(this) && outer.getValue(ENABLED) != !empty) {
            level.setBlock(bePos, outer.setValue(ENABLED, !empty), 3);
        } else {
            level.sendBlockUpdated(bePos, outer, outer, 3);
        }
    }

    private void handleBucketExchange(Player player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (held.getCount() == 1) {
            player.setItemInHand(hand, result);
        } else {
            held.shrink(1);
            if (!player.addItem(result)) {
                player.drop(result, false);
            }
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
        // 0) クワ右クリックで本体回収 (唯一の回収手段。中身ありでは外枠は絶対に壊れないため)
        // 空でも回収可。クリエは消費なし。
        if (!held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.HoeItem) {
            ItemStack stack = new ItemStack(this);
            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(MiniatureBlockEntity.TAG_SIZE, be.getSize());
            if (!be.isEmpty()) {
                net.minecraft.nbt.CompoundTag bet = new net.minecraft.nbt.CompoundTag();
                be.writeSyncData(bet);
                tag.put(BLOCK_ENTITY_TAG, bet);
            }
            // クリエ以外は手持ちを減らさない (クワは消耗しない)
            if (!player.addItem(stack)) {
                Block.popResource(level, pos, stack);
            }
            ALLOW_HOE_REMOVAL.set(true);
            try {
                level.removeBlock(pos, false);
            } finally {
                ALLOW_HOE_REMOVAL.set(false);
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
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
        // ドア等の DOUBLE_BLOCK_HALF は上下連動が必須のため、相方も同時トグルする
        BlockState targetState = be.getCell(targetPos);
        if (!targetState.isAir() && MiniatureWhitelist.canInteract(targetState)) {
            BlockState toggled = MiniatureWhitelist.toggleInteractable(targetState);
            if (toggled != null) {
                be.setCell(targetPos, toggled);
                // DOUBLE_BLOCK_HALF を持つブロック (ドア等) は相方も同期
                if (toggled.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    try {
                        var half = toggled.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                        BlockPos otherPos = half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                                ? targetPos.above() : targetPos.below();
                        if (be.isInRange(otherPos.getX(), otherPos.getY(), otherPos.getZ())) {
                            BlockState other = be.getCell(otherPos);
                            if (!other.isAir() && other.is(toggled.getBlock())
                                    && other.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                                    && other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != half) {
                                // 相方の OPEN/POWERED 等を toggled と同期
                                BlockState otherToggled = other;
                                // OPEN を同期 (ドア)
                                if (other.hasProperty(BlockStateProperties.OPEN)
                                        && toggled.hasProperty(BlockStateProperties.OPEN)) {
                                    otherToggled = otherToggled.setValue(BlockStateProperties.OPEN,
                                            toggled.getValue(BlockStateProperties.OPEN));
                                }
                                // POWERED も同期 (必要なら)
                                if (other.hasProperty(BlockStateProperties.POWERED)
                                        && toggled.hasProperty(BlockStateProperties.POWERED)) {
                                    otherToggled = otherToggled.setValue(BlockStateProperties.POWERED,
                                            toggled.getValue(BlockStateProperties.POWERED));
                                } else if (!otherToggled.equals(other)) {
                                    // OPEN 同期で既に変化がある場合はそのまま、なければ toggle を試す
                                } else {
                                    BlockState tmp = MiniatureWhitelist.toggleInteractable(other);
                                    if (tmp != null) otherToggled = tmp;
                                }
                                if (!otherToggled.equals(other)) {
                                    be.setCell(otherPos, otherToggled);
                                    updateInnerConnections(otherPos, be, level, pos);
                                }
                            }
                        }
                    } catch (Exception e) {}
                }
                updateInnerConnections(targetPos, be, level, pos);
                be.rebuildShapeCache();
                level.sendBlockUpdated(pos, state, state, 3);
                // ドア等の開閉音はバニラの DoorBlock.use が level.playSound するため、ここでは省略
                return InteractionResult.SUCCESS;
            }
        }

        // 2.5) 骨粉対応 — ミニチュア内の苗木/作物に適用 (sakura DummyItemUseContext 相当を簡易再現)
        if (held.is(Items.BONE_MEAL)) {
            BlockState inner = be.getCell(targetPos);
            if (!inner.isAir()) {
                boolean grew = applyBonemealInMiniature(be, targetPos, inner, level);
                if (grew) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    be.rebuildShapeCache();
                    level.sendBlockUpdated(pos, state, state, 3);
                    return InteractionResult.SUCCESS;
                }
                // 成長しなかった場合はスルーして通常処理へ (PASS)
            }
        }

        // 2.6) バケツ対応 — 再実装 (2026-08-26)
        // 水はフルキューブではない (LiquidBlock.LEVEL相当の height 可変、corner平均)、テクスチャは still/flow の
        // 2枚で毎tickアニメ、RenderType は translucent (ItemBlockRenderTypes.getRenderLayer) が正しいパス。
        // 旧実装の LEVEL 段階操作(0→1→...→空気で流動アニメを偽装)は無駄で見た目も崩れるため廃止。単純に
        //  - 置く: 空セルへ Blocks.WATER/LAVA もしくは fluid.defaultFluidState().createLegacyBlock() を1回で設置、
        //         waterlogged な水は STATE.setValue(WATERLOGGED,true) のみ。
        //  - 掬う: FluidState.isEmpty() で判定し該当セルを AIR へ、waterlogged は false へ。
        // で完結する。アニメ/height は Renderer 側で LiquidBlockRenderer 相当に計算するためここでは触らない。
        if (!held.isEmpty() && held.getItem() instanceof BucketItem bucketItem) {
            Fluid fluid = bucketItem.getFluid();
            boolean isEmpty = fluid == Fluids.EMPTY;
            BlockState tgtState = be.getCell(targetPos);
            BlockState hitState = be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) ? be.getCell(hitPos) : null;
            if (isEmpty) {
                // 採取: FluidState があるセルを優先 (target → hit)、次に waterlogged
                BlockState pickupState = null;
                BlockPos pickupPos = null;
                if (tgtState != null && !tgtState.getFluidState().isEmpty()) {
                    pickupState = tgtState;
                    pickupPos = targetPos;
                } else if (hitState != null && !hitState.getFluidState().isEmpty()) {
                    pickupState = hitState;
                    pickupPos = hitPos;
                } else if (tgtState != null && tgtState.hasProperty(BlockStateProperties.WATERLOGGED) && tgtState.getValue(BlockStateProperties.WATERLOGGED)) {
                    pickupState = tgtState;
                    pickupPos = targetPos;
                } else if (hitState != null && hitState.hasProperty(BlockStateProperties.WATERLOGGED) && hitState.getValue(BlockStateProperties.WATERLOGGED)) {
                    pickupState = hitState;
                    pickupPos = hitPos;
                }
                if (pickupState != null && pickupPos != null) {
                    // waterlogged は WATER として掬う
                    if (pickupState.hasProperty(BlockStateProperties.WATERLOGGED) && pickupState.getValue(BlockStateProperties.WATERLOGGED)) {
                        BlockState ns = pickupState.setValue(BlockStateProperties.WATERLOGGED, false);
                        be.setCell(pickupPos, ns);
                        updateInnerConnections(pickupPos, be, level, pos);
                        be.rebuildShapeCache();
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        handleBucketExchange(player, hand, held, new ItemStack(Items.WATER_BUCKET));
                        return InteractionResult.SUCCESS;
                    }
                    FluidState fs = pickupState.getFluidState();
                    if (!fs.isEmpty() || pickupState.is(Blocks.WATER) || pickupState.is(Blocks.LAVA)) {
                        boolean isLava = fs.is(Fluids.LAVA) || pickupState.is(Blocks.LAVA);
                        be.setCell(pickupPos, Blocks.AIR.defaultBlockState());
                        validCheck(be, pickupPos, player);
                        // 外側 ENABLED 更新
                        boolean empty = be.isEmpty();
                        BlockState outer = level.getBlockState(pos);
                        if (outer.is(this) && outer.getValue(ENABLED) != !empty) {
                            level.setBlock(pos, outer.setValue(ENABLED, !empty), 3);
                        } else {
                            level.sendBlockUpdated(pos, state, state, 3);
                        }
                        be.rebuildShapeCache();
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        handleBucketExchange(player, hand, held, new ItemStack(isLava ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
                        return InteractionResult.SUCCESS;
                    }
                }
            } else {
                // 給水/給溶岩
                // waterlogged への直接給水を優先 (target → hit)
                if (fluid == Fluids.WATER) {
                    if (tgtState.hasProperty(BlockStateProperties.WATERLOGGED) && !tgtState.getValue(BlockStateProperties.WATERLOGGED)) {
                        be.setCell(targetPos, tgtState.setValue(BlockStateProperties.WATERLOGGED, true));
                        updateInnerConnections(targetPos, be, level, pos);
                        be.rebuildShapeCache();
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        handleBucketExchange(player, hand, held, new ItemStack(Items.BUCKET));
                        return InteractionResult.SUCCESS;
                    }
                    if (hitState != null && hitState.hasProperty(BlockStateProperties.WATERLOGGED) && !hitState.getValue(BlockStateProperties.WATERLOGGED)) {
                        be.setCell(hitPos, hitState.setValue(BlockStateProperties.WATERLOGGED, true));
                        updateInnerConnections(hitPos, be, level, pos);
                        be.rebuildShapeCache();
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        handleBucketExchange(player, hand, held, new ItemStack(Items.BUCKET));
                        return InteractionResult.SUCCESS;
                    }
                }
                BlockPos placePos = null;
                if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && be.getCell(hitPos).isAir()) {
                    placePos = hitPos;
                } else if (be.isInRange(targetPos.getX(), targetPos.getY(), targetPos.getZ()) && be.getCell(targetPos).isAir()) {
                    placePos = targetPos;
                }
                if (placePos != null) {
                    BlockState fluidBlock;
                    if (fluid == Fluids.WATER) fluidBlock = Blocks.WATER.defaultBlockState();
                    else if (fluid == Fluids.LAVA) fluidBlock = Blocks.LAVA.defaultBlockState();
                    else fluidBlock = fluid.defaultFluidState().createLegacyBlock();
                    // LEVEL は触らない。createLegacyBlock は source (LEVEL 0) を返す。
                    be.setCell(placePos, fluidBlock);
                    updateInnerConnections(placePos, be, level, pos);
                    be.rebuildShapeCache();
                    BlockState outer = level.getBlockState(pos);
                    if (outer.is(this) && !outer.getValue(ENABLED)) {
                        level.setBlock(pos, outer.setValue(ENABLED, true), 3);
                    } else {
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    handleBucketExchange(player, hand, held, new ItemStack(Items.BUCKET));
                    return InteractionResult.SUCCESS;
                }
                // 空セル無し & waterlogged 不可 → 失敗
                return InteractionResult.FAIL;
            }
        }

        // 3) 手持ちが BlockItem → 配置 (面を考慮: BlockStateForPlacement で向きを解決)
        if (!held.isEmpty() && held.getItem() instanceof BlockItem blockItem) {
            // 既にoccupiedなセルには置けない
            BlockPos placePos = null;
            // hitPos が空かつ範囲内ならそこへ、そうでなければ targetPos が空ならそこへ (簡易)
            if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && be.getCell(hitPos).isAir()) {
                placePos = hitPos;
            } else if (be.isInRange(targetPos.getX(), targetPos.getY(), targetPos.getZ()) && be.getCell(targetPos).isAir()) {
                placePos = targetPos;
            }
            if (placePos != null) {
                Block block = blockItem.getBlock();
                BlockState toPlace;
                BlockPlaceContext ctx = null;
                // 面を考慮した BlockState 解決: BlockPlaceContext を外側レベルで生成し、hit面/プレイヤー向きを反映
                // sakura Miniature は DummyItemUseContext + innerWorld + tryPlace で同様のことを行っている
                // 内側の canSurvive は FakeReader で再判定するため、外側での失敗は後で補正する
                try {
                    ctx = new BlockPlaceContext(level, player, hand, held, hit);
                    BlockState tmp = block.getStateForPlacement(ctx);
                    if (tmp != null) {
                        toPlace = tmp;
                    } else {
                        toPlace = block.defaultBlockState();
                    }
                } catch (Exception e) {
                    toPlace = block.defaultBlockState();
                }
                // whitelist (向き解決後の State で判定。default と異なる場合があるため)
                if (!MiniatureWhitelist.canPlace(toPlace)) {
                    // 面解決前の default が許可される場合の救済: default も不可なら FAIL
                    if (!MiniatureWhitelist.canPlace(block.defaultBlockState())) {
                        return InteractionResult.FAIL;
                    } else {
                        // whitelist側では FACING違いは同一ブロック扱いで許可されるため、toPlace を通す
                    }
                }
                // 吸着チェック — 汎用 canSurvive (FakeReader)。特定ブロック分岐を廃止。
                // 壁トーチ等は facing を自動で補正する。BlockPlaceContext の面情報（getNearestLookingDirections）を用いる。
                // 下にブロックがあっても壁クリック時は壁付けを優先する。
                {
                    MiniatureFakeLevelReader fake = new MiniatureFakeLevelReader(be, level, pos);
                    Direction[] preferred = ctx != null ? ctx.getNearestLookingDirections() : new Direction[]{hit.getDirection()};
                    boolean isWallClick = hit.getDirection().getAxis().isHorizontal();
                    boolean isStandingWallItem = blockItem instanceof net.minecraft.world.item.StandingAndWallBlockItem;
                    if (isStandingWallItem && isWallClick) {
                        // 壁クリック時は壁変種を優先（床があっても壁付け）
                        BlockState wallFixed = tryWallVariant(block, blockItem, fake, placePos, preferred);
                        if (wallFixed != null && wallFixed.canSurvive(fake, placePos)) {
                            toPlace = wallFixed;
                        } else if (!toPlace.canSurvive(fake, placePos)) {
                            BlockState fixed = findValidPlacementState(block, toPlace, fake, placePos, preferred);
                            if (fixed != null) toPlace = fixed;
                            else return InteractionResult.FAIL;
                        }
                        // 壁付けが見つからず床も不可なら FAIL は上記で処理、見つかった場合はそのまま
                        if (!toPlace.canSurvive(fake, placePos)) return InteractionResult.FAIL;
                    } else {
                        if (!toPlace.canSurvive(fake, placePos)) {
                            BlockState fixed = findValidPlacementState(block, toPlace, fake, placePos, preferred);
                            if (fixed != null) {
                                toPlace = fixed;
                            } else {
                                BlockState wallFixed = tryWallVariant(block, blockItem, fake, placePos, preferred);
                                if (wallFixed != null) {
                                    toPlace = wallFixed;
                                } else {
                                    return InteractionResult.FAIL;
                                }
                            }
                        }
                    }
                }
                // 二重占有ブロック(扉/背の高い花/ベッド)の自動配置 — 上下連携を汎用化
                if (toPlace.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    DoubleBlockHalf half = toPlace.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                    if (half == DoubleBlockHalf.LOWER) {
                        BlockPos upperPos = placePos.above();
                        if (!be.isInRange(upperPos.getX(), upperPos.getY(), upperPos.getZ())
                                || !be.getCell(upperPos).isAir()) {
                            return InteractionResult.FAIL;
                        }
                        BlockState upperState = toPlace.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                                DoubleBlockHalf.UPPER);
                        // 下段は既に generic canSurvive でチェック済み。上段は下段が存在して初めて survive する
                        // (DoorBlock は上段の canSurvive で下段が Door 下半かを確認) ため、一時的に下段を置いて判定
                        BlockState prevLower = be.getCell(placePos);
                        be.setCell(placePos, toPlace);
                        boolean upperOk = canSurviveInside(upperState, upperPos, be, level, pos);
                        if (!upperOk) {
                            be.setCell(placePos, prevLower);
                            return InteractionResult.FAIL;
                        }
                        // 上段を配置 (下段は既に置かれている)
                        if (be.setCell(upperPos, upperState)) {
                            updateInnerConnections(placePos, be, level, pos);
                            updateInnerConnections(upperPos, be, level, pos);
                            be.rebuildShapeCache();
                            if (!player.getAbilities().instabuild) {
                                held.shrink(1);
                            }
                            BlockState newState = level.getBlockState(pos);
                            if (newState.is(this) && !newState.getValue(ENABLED)) {
                                level.setBlock(pos, newState.setValue(ENABLED, true), 3);
                            } else {
                                level.sendBlockUpdated(pos, state, state, 3);
                            }
                            return InteractionResult.SUCCESS;
                        } else {
                            be.setCell(placePos, prevLower);
                            return InteractionResult.FAIL;
                        }
                    } else {
                        // UPPER 単体が要求された場合は LOWER へ正規化して再試行 (通常は LOWER のみが getStateForPlacement で返る)
                        return InteractionResult.FAIL;
                    }
                }
                if (toPlace.hasProperty(BlockStateProperties.BED_PART)) {
                    BedPart part = toPlace.getValue(BlockStateProperties.BED_PART);
                    if (part == BedPart.FOOT) {
                        // ミニチュア内では外側 BlockPlaceContext (miniature外殻位置) の getStateForPlacement が
                        // 外側ワールドの replaceable/sturdy 判定で null → default(NORTH) にフォールバックし
                        // プレイヤー向きが失われる。布団は向きが重要なので、常にプレイヤー向きで上書きする。
                        Direction bedFacing = ctx != null ? ctx.getHorizontalDirection() : player.getDirection();
                        // HORIZONTAL_FACING を持つ場合はプレイヤー向きに正規化し、canSurvive を満たす向きを優先
                        if (toPlace.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            // 一旦プレイヤー向きで試し、生存不可なら findValidPlacementState 的に回す
                            BlockState withFacing = toPlace.setValue(BlockStateProperties.HORIZONTAL_FACING, bedFacing);
                            MiniatureFakeLevelReader fakeForFacing = new MiniatureFakeLevelReader(be, level, pos);
                            if (withFacing.canSurvive(fakeForFacing, placePos)) {
                                toPlace = withFacing;
                            } else {
                                // プレイヤー向きで生存不可なら他向きを試す (壁際等)
                                BlockState fixed = findValidPlacementState(toPlace.getBlock(), withFacing, fakeForFacing, placePos, bedFacing);
                                if (fixed != null) {
                                    toPlace = fixed;
                                    bedFacing = fixed.getValue(BlockStateProperties.HORIZONTAL_FACING);
                                } else {
                                    // フォールバックはプレイヤー向きのまま (head 側で FAIL になる)
                                    toPlace = withFacing;
                                }
                            }
                        } else {
                            bedFacing = toPlace.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                                    ? toPlace.getValue(BlockStateProperties.HORIZONTAL_FACING)
                                    : bedFacing;
                        }
                        BlockPos headPos = placePos.relative(bedFacing);
                        if (!be.isInRange(headPos.getX(), headPos.getY(), headPos.getZ())
                                || !be.getCell(headPos).isAir()) {
                            return InteractionResult.FAIL;
                        }
                        BlockState headState = toPlace.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                        if (!canSurviveInside(headState, headPos, be, level, pos)) {
                            return InteractionResult.FAIL;
                        }
                        if (be.setCell(placePos, toPlace) && be.setCell(headPos, headState)) {
                            updateInnerConnections(placePos, be, level, pos);
                            updateInnerConnections(headPos, be, level, pos);
                            be.rebuildShapeCache();
                            if (!player.getAbilities().instabuild) {
                                held.shrink(1);
                            }
                            BlockState newState = level.getBlockState(pos);
                            if (newState.is(this) && !newState.getValue(ENABLED)) {
                                level.setBlock(pos, newState.setValue(ENABLED, true), 3);
                            } else {
                                level.sendBlockUpdated(pos, state, state, 3);
                            }
                            return InteractionResult.SUCCESS;
                        }
                        return InteractionResult.FAIL;
                    }
                }
                // 通常単一ブロック
                if (be.setCell(placePos, toPlace)) {
                    updateInnerConnections(placePos, be, level, pos);
                    be.rebuildShapeCache();
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
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

        // 右クリック素手での内部破壊は廃止 — 左クリック (attack) で行う
        // 何もしないで PASS を返すことで、左クリック側の破壊に委譲する
        return InteractionResult.PASS;
    }

    // 依存ブロックの落下・吸着チェック (ドア/松明/レッドストーン等の canSurvive)
    private void validCheck(MiniatureBlockEntity be, BlockPos removedPos, Player player) {
        // removedPos の周辺6方向を走査し、支持を失ったブロックを除去
        for (Direction dir : Direction.values()) {
            BlockPos n = removedPos.relative(dir);
            if (!be.isInRange(n.getX(), n.getY(), n.getZ())) {
                continue;
            }
            BlockState ns = be.getCell(n);
            if (ns.isAir()) {
                continue;
            }
            try {
                // ドア等の上半分は下が空なら落下 (従来維持)
                if (ns.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    DoubleBlockHalf half = ns.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                    if (half == DoubleBlockHalf.UPPER) {
                        BlockPos below = n.below();
                        if (!be.isInRange(below.getX(), below.getY(), below.getZ()) || be.getCell(below).isAir()) {
                            be.setCell(n, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                            validCheck(be, n, player);
                            continue;
                        }
                    }
                }
                // 汎用 canSurvive チェック (松明/レッドストーン等の吸着)
                Level outerLevel = be.getLevel();
                BlockPos bePos = be.getBlockPos();
                if (outerLevel != null && bePos != null) {
                    if (!canSurviveInside(ns, n, be, outerLevel, bePos)) {
                        be.setCell(n, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                        validCheck(be, n, player);
                    }
                }
            } catch (Exception e) {
                // 無視
            }
        }
    }

    /**
     * ミニチュア内での canSurvive 判定 — 汎用版。
     * 特定ブロック分岐を廃止し、{@code state.canSurvive(reader, pos)} に委譲する。
     * 内側は BE配列、境界外は実ワールドを見る FakeReader を使用。
     * これにより壁トーチ/ランタン/レッドストーン等の吸着をバニラロジック通りに判定できる。
     */
    private boolean canSurviveInside(BlockState state, BlockPos innerPos, MiniatureBlockEntity be, Level outerLevel,
            BlockPos bePos) {
        try {
            MiniatureFakeLevelReader fake = new MiniatureFakeLevelReader(be, outerLevel, bePos);
            return state.canSurvive(fake, innerPos);
        } catch (Exception e) {
            // canSurvive が例外を投げる場合 (未対応ブロック) は生存可能とみなす
            return true;
        }
    }

    /**
     * 配置時に canSurvive を満たす BlockState を汎用的に探索。
     * HORIZONTAL_FACING / FACING を持つブロックは全方向を試行し、最初に survive するものを返す。
     * hitDir が与えられればその方向を優先して試行する (壁トーチ等のクリック面追従)。
     */
    private BlockState findValidPlacementState(Block block, BlockState attempted, MiniatureFakeLevelReader fake, BlockPos innerPos, Direction hitDir) {
        // まず hitDir を優先して試す
        if (hitDir != null && attempted != null) {
            if (attempted.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && hitDir.getAxis().isHorizontal()) {
                try {
                    BlockState s = attempted.setValue(BlockStateProperties.HORIZONTAL_FACING, hitDir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
            if (attempted.hasProperty(BlockStateProperties.FACING)) {
                try {
                    BlockState s = attempted.setValue(BlockStateProperties.FACING, hitDir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
        }
        if (attempted != null) {
            try {
                if (attempted.canSurvive(fake, innerPos)) return attempted;
            } catch (Exception e) {}
            // attempted の facing を回して試す
            if (attempted.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (hitDir != null && dir == hitDir) continue; // 既に試した
                    try {
                        BlockState s = attempted.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
            if (attempted.hasProperty(BlockStateProperties.FACING)) {
                for (Direction dir : Direction.values()) {
                    if (hitDir != null && dir == hitDir) continue;
                    try {
                        BlockState s = attempted.setValue(BlockStateProperties.FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
        }
        BlockState def = block.defaultBlockState();
        if (hitDir != null) {
            if (def.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && hitDir.getAxis().isHorizontal()) {
                try {
                    BlockState s = def.setValue(BlockStateProperties.HORIZONTAL_FACING, hitDir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
            if (def.hasProperty(BlockStateProperties.FACING)) {
                try {
                    BlockState s = def.setValue(BlockStateProperties.FACING, hitDir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
        }
        try {
            if (def.canSurvive(fake, innerPos)) return def;
        } catch (Exception e) {}
        if (def.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (hitDir != null && dir == hitDir) continue;
                try {
                    BlockState s = def.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
        }
        if (def.hasProperty(BlockStateProperties.FACING)) {
            for (Direction dir : Direction.values()) {
                if (hitDir != null && dir == hitDir) continue;
                try {
                    BlockState s = def.setValue(BlockStateProperties.FACING, dir);
                    if (s.canSurvive(fake, innerPos)) return s;
                } catch (Exception e) {}
            }
        }
        return null;
    }

    private BlockState findValidPlacementState(Block block, BlockState attempted, MiniatureFakeLevelReader fake, BlockPos innerPos) {
        return findValidPlacementState(block, attempted, fake, innerPos, (Direction) null);
    }

    private BlockState findValidPlacementState(Block block, BlockState attempted, MiniatureFakeLevelReader fake, BlockPos innerPos, Direction[] preferred) {
        if (preferred != null) {
            // preferred 順で attempted の facing を試す
            for (Direction dir : preferred) {
                if (attempted != null && attempted.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir.getAxis().isHorizontal()) {
                    try {
                        BlockState s = attempted.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
                if (attempted != null && attempted.hasProperty(BlockStateProperties.FACING)) {
                    try {
                        BlockState s = attempted.setValue(BlockStateProperties.FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
            BlockState def = block.defaultBlockState();
            for (Direction dir : preferred) {
                if (def.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir.getAxis().isHorizontal()) {
                    try {
                        BlockState s = def.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
                if (def.hasProperty(BlockStateProperties.FACING)) {
                    try {
                        BlockState s = def.setValue(BlockStateProperties.FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
        }
        return null;
    }

    private BlockState tryWallVariant(Block block, BlockItem item, MiniatureFakeLevelReader fake, BlockPos innerPos, Direction hitDir) {
        return tryWallVariant(block, item, fake, innerPos, hitDir == null ? null : new Direction[]{hitDir});
    }

    /**
     * StandingAndWall 系 (松明) の壁変種を試行。TORCH <-> WALL_TORCH の相互補完。
     * preferred があればその順で優先する (BlockPlaceContext.getNearestLookingDirections() 由来)。
     */
    private BlockState tryWallVariant(Block block, BlockItem item, MiniatureFakeLevelReader fake, BlockPos innerPos, Direction[] preferred) {
        Block wall = null;
        Block standing = null;
        if (block == Blocks.TORCH) wall = Blocks.WALL_TORCH;
        else if (block == Blocks.SOUL_TORCH) wall = Blocks.SOUL_WALL_TORCH;
        else if (block == Blocks.REDSTONE_TORCH) wall = Blocks.REDSTONE_WALL_TORCH;
        else if (block == Blocks.WALL_TORCH) standing = Blocks.TORCH;
        else if (block == Blocks.SOUL_WALL_TORCH) standing = Blocks.SOUL_TORCH;
        else if (block == Blocks.REDSTONE_WALL_TORCH) standing = Blocks.REDSTONE_TORCH;
        else {
            // StandingAndWallBlockItem の wallBlock をリフレクションで取得
            if (item instanceof net.minecraft.world.item.StandingAndWallBlockItem) {
                try {
                    var f = net.minecraft.world.item.StandingAndWallBlockItem.class.getDeclaredField("wallBlock");
                    f.setAccessible(true);
                    Object wb = f.get(item);
                    if (wb instanceof Block b) wall = b;
                } catch (Exception e) {}
            }
        }
        if (wall != null) {
            BlockState def = wall.defaultBlockState();
            if (preferred != null) {
                for (Direction dir : preferred) {
                    if (!dir.getAxis().isHorizontal() || !def.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) continue;
                    try {
                        BlockState s = def.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
            if (def.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    boolean skip = false;
                    if (preferred != null) {
                        for (Direction p : preferred) if (p == dir) { skip = true; break; }
                    }
                    if (skip) continue;
                    try {
                        BlockState s = def.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
                        if (s.canSurvive(fake, innerPos)) return s;
                    } catch (Exception e) {}
                }
            }
            try {
                if (def.canSurvive(fake, innerPos)) return def;
            } catch (Exception e) {}
        }
        if (standing != null) {
            BlockState def = standing.defaultBlockState();
            try {
                if (def.canSurvive(fake, innerPos)) return def;
            } catch (Exception e) {}
        }
        return null;
    }

    private BlockState getSupportState(BlockPos supportPos, MiniatureBlockEntity be, Level outerLevel, BlockPos bePos) {
        if (be.isInRange(supportPos.getX(), supportPos.getY(), supportPos.getZ())) {
            return be.getCell(supportPos);
        } else {
            // 境界外は実ワールドを見る (sakura fixPos ロジック簡易版)
            int fx = supportPos.getX() < 0 ? -1 : supportPos.getX() >= be.getSize() ? 1 : 0;
            int fy = supportPos.getY() < 0 ? -1 : supportPos.getY() >= be.getSize() ? 1 : 0;
            int fz = supportPos.getZ() < 0 ? -1 : supportPos.getZ() >= be.getSize() ? 1 : 0;
            BlockPos outerPos = bePos.offset(fx, fy, fz);
            try {
                return outerLevel.getBlockState(outerPos);
            } catch (Exception e) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
    }

    private boolean isSupportSturdy(BlockState support, Direction dir) {
        if (support.isAir()) {
            return false;
        }
        try {
            if (support.canOcclude()) {
                return true;
            }
            return !support.isAir() && support.getFluidState().isEmpty();
        } catch (Exception e) {
            return !support.isAir();
        }
    }

    // ===== 内部接続更新 (フェンス/壁/パネ/レッドストーン) =====

    private void updateInnerConnections(BlockPos center, MiniatureBlockEntity be, Level outerLevel, BlockPos bePos) {
        BlockPos[] toUpdate = new BlockPos[] {
                center, center.above(), center.below(), center.north(), center.south(), center.east(), center.west()
        };
        for (BlockPos p : toUpdate) {
            if (!be.isInRange(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            BlockState st = be.getCell(p);
            if (st.isAir()) {
                continue;
            }
            BlockState ns = updateStateConnections(st, p, be, outerLevel, bePos);
            if (ns != st) {
                be.setCell(p.getX(), p.getY(), p.getZ(), ns);
            }
        }
    }

    private BlockState updateStateConnections(BlockState state, BlockPos pos, MiniatureBlockEntity be, Level outerLevel,
            BlockPos bePos) {
        Block b = state.getBlock();
        if (b instanceof CrossCollisionBlock) {
            BlockState ns = state;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos nPos = pos.relative(dir);
                BlockState nState = getSupportState(nPos, be, outerLevel, bePos);
                boolean shouldConnect = shouldConnectCross(nState);
                var prop = getCrossProperty(dir);
                if (prop != null && ns.hasProperty(prop)) {
                    boolean cur = ns.getValue(prop);
                    if (cur != shouldConnect) {
                        ns = ns.setValue(prop, shouldConnect);
                    }
                }
            }
            if (b instanceof WallBlock) {
                try {
                    BlockPos upPos = pos.above();
                    BlockState upState = getSupportState(upPos, be, outerLevel, bePos);
                    boolean upConnect = !upState.isAir();
                    if (ns.hasProperty(WallBlock.UP)) {
                        boolean curUp = ns.getValue(WallBlock.UP);
                        if (curUp != upConnect) {
                            ns = ns.setValue(WallBlock.UP, upConnect);
                        }
                    }
                    // Wall height properties LOW/TALL/NONE も簡易に LOW に
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        var heightProp = getWallHeightProperty(dir);
                        if (heightProp != null && ns.hasProperty(heightProp)) {
                            var curH = ns.getValue(heightProp);
                            var want = shouldConnectCross(getSupportState(pos.relative(dir), be, outerLevel, bePos))
                                    ? net.minecraft.world.level.block.state.properties.WallSide.LOW
                                    : net.minecraft.world.level.block.state.properties.WallSide.NONE;
                            if (curH != want) {
                                ns = ns.setValue(heightProp, want);
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
            return ns;
        }
        if (b instanceof RedStoneWireBlock) {
            BlockState ns = state;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos nPos = pos.relative(dir);
                BlockState nState = getSupportState(nPos, be, outerLevel, bePos);
                net.minecraft.world.level.block.state.properties.RedstoneSide side = getRedstoneSideFor(nPos, nState, be, outerLevel, bePos, dir);
                var prop = getRedstoneProperty(dir);
                if (prop != null && ns.hasProperty(prop)) {
                    var cur = ns.getValue(prop);
                    if (cur != side) {
                        ns = ns.setValue(prop, side);
                    }
                }
            }
            return ns;
        }
        return state;
    }

    private boolean shouldConnectCross(BlockState neighbor) {
        if (neighbor.isAir()) {
            return false;
        }
        Block nb = neighbor.getBlock();
        if (nb instanceof CrossCollisionBlock) {
            return true;
        }
        // FenceGate、固体ブロックも接続
        if (neighbor.canOcclude()) {
            return true;
        }
        // 例: レッドストーン系や観察者なども固体とみなすが、ここでは canOcclude でカバー
        return false;
    }

    private net.minecraft.world.level.block.state.properties.BooleanProperty getCrossProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH;
            case SOUTH -> net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH;
            case EAST -> net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST;
            case WEST -> net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST;
            default -> null;
        };
    }

    private net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.WallSide> getWallHeightProperty(
            Direction dir) {
        return switch (dir) {
            case NORTH -> WallBlock.NORTH_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> null;
        };
    }

    private net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.RedstoneSide> getRedstoneProperty(
            Direction dir) {
        return switch (dir) {
            case NORTH -> RedStoneWireBlock.NORTH;
            case SOUTH -> RedStoneWireBlock.SOUTH;
            case EAST -> RedStoneWireBlock.EAST;
            case WEST -> RedStoneWireBlock.WEST;
            default -> null;
        };
    }

    private net.minecraft.world.level.block.state.properties.RedstoneSide getRedstoneSideFor(BlockPos nPos,
            BlockState nState, MiniatureBlockEntity be, Level outerLevel, BlockPos bePos, Direction dir) {
        if (nState.getBlock() instanceof RedStoneWireBlock) {
            return net.minecraft.world.level.block.state.properties.RedstoneSide.SIDE;
        }
        BlockPos upPos = nPos.above();
        BlockState upState = getSupportState(upPos, be, outerLevel, bePos);
        if (upState.getBlock() instanceof RedStoneWireBlock) {
            if (!nState.isAir() && nState.canOcclude()) {
                return net.minecraft.world.level.block.state.properties.RedstoneSide.UP;
            }
        }
        BlockPos downPos = nPos.below();
        // 下にワイヤがあって固体を介して接続する場合も考慮するが簡易では NONE
        return net.minecraft.world.level.block.state.properties.RedstoneSide.NONE;
    }

    // ===== 骨粉 (ミニチュア内) =====

    private boolean applyBonemealInMiniature(MiniatureBlockEntity be, BlockPos innerPos, BlockState innerState,
            Level level) {
        Block block = innerState.getBlock();
        // Crop (小麦/じゃがいも/にんじん/ビート等) — 年齢を進める
        if (block instanceof CropBlock) {
            for (Property<?> prop : innerState.getProperties()) {
                if (prop.getName().equals("age") && prop instanceof IntegerProperty ip) {
                    int age = innerState.getValue(ip);
                    int max = java.util.Collections.max(ip.getPossibleValues());
                    if (age < max) {
                        // バニラ骨粉は +2〜5 程度だが、ミニチュアでは確実に成長させるため +1〜2
                        int add = 1 + level.getRandom().nextInt(2);
                        int next = Math.min(max, age + add);
                        BlockState ns = innerState.setValue(ip, next);
                        be.setCell(innerPos, ns);
                        return true;
                    }
                    return false;
                }
            }
        }
        // Sapling — STAGE 0→1、1→簡易樹木生成
        if (block instanceof SaplingBlock) {
            if (innerState.hasProperty(BlockStateProperties.STAGE)) {
                int stage = innerState.getValue(BlockStateProperties.STAGE);
                if (stage == 0) {
                    be.setCell(innerPos, innerState.setValue(BlockStateProperties.STAGE, 1));
                    return true;
                } else {
                    // 2回目の骨粉で樹木生成を試みる
                    boolean grew = tryGrowSaplingToTree(be, innerPos, innerState);
                    if (grew) {
                        return true;
                    }
                    // 空間不足なら stage を維持したまま成功扱いにはしない
                    return false;
                }
            }
        }
        // その他 Bonemealable (サトウキビ/竹/海草等は対象外、汎用は最終試行)
        if (block instanceof BonemealableBlock bm) {
            // 水中/土壌判定は省略し、簡易に perform を試すための偽装
            // Crop/Sapling は上記で処理済みのため、ここでは汎用に age 類似を探す
            for (Property<?> prop : innerState.getProperties()) {
                if ((prop.getName().equals("age") || prop.getName().equals("growth"))
                        && prop instanceof IntegerProperty ip) {
                    int v = innerState.getValue(ip);
                    int max = java.util.Collections.max(ip.getPossibleValues());
                    if (v < max) {
                        be.setCell(innerPos, innerState.setValue(ip, Math.min(max, v + 1)));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean tryGrowSaplingToTree(MiniatureBlockEntity be, BlockPos innerPos, BlockState saplingState) {
        Block sapling = saplingState.getBlock();
        Block log;
        Block leaves;
        // mod苗木の対応付け
        if (sapling == ruby.bamboo.core.init.BambooBlocks.SAKURA_SAPLING.get()
                || sapling == net.minecraft.world.level.block.Blocks.OAK_SAPLING) {
            // 桜は特別、他は桜苗木ではないので OAK 判定は後段へ
        }
        if (sapling == ruby.bamboo.core.init.BambooBlocks.SAKURA_SAPLING.get()) {
            log = ruby.bamboo.core.init.BambooBlocks.SAKURA_LOG.get();
            leaves = ruby.bamboo.core.init.BambooBlocks.SAKURA_LEAVES.get();
        } else if (sapling == ruby.bamboo.core.init.BambooBlocks.MAPLE_SAPLING.get()) {
            log = ruby.bamboo.core.init.BambooBlocks.MAPLE_LOG.get();
            leaves = ruby.bamboo.core.init.BambooBlocks.MAPLE_LEAVES.get();
        } else if (sapling == ruby.bamboo.core.init.BambooBlocks.GINKGO_SAPLING.get()) {
            log = ruby.bamboo.core.init.BambooBlocks.GINKGO_LOG.get();
            leaves = ruby.bamboo.core.init.BambooBlocks.GINKGO_LEAVES.get();
        } else if (sapling == ruby.bamboo.core.init.BambooBlocks.HINOKI_SAPLING.get()) {
            log = ruby.bamboo.core.init.BambooBlocks.HINOKI_LOG.get();
            leaves = ruby.bamboo.core.init.BambooBlocks.HINOKI_LEAVES.get();
        } else if (sapling == net.minecraft.world.level.block.Blocks.OAK_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.OAK_LOG;
            leaves = net.minecraft.world.level.block.Blocks.OAK_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.BIRCH_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.BIRCH_LOG;
            leaves = net.minecraft.world.level.block.Blocks.BIRCH_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.SPRUCE_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.SPRUCE_LOG;
            leaves = net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.JUNGLE_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.JUNGLE_LOG;
            leaves = net.minecraft.world.level.block.Blocks.JUNGLE_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.ACACIA_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.ACACIA_LOG;
            leaves = net.minecraft.world.level.block.Blocks.ACACIA_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.DARK_OAK_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.DARK_OAK_LOG;
            leaves = net.minecraft.world.level.block.Blocks.DARK_OAK_LEAVES;
        } else if (sapling == net.minecraft.world.level.block.Blocks.CHERRY_SAPLING) {
            log = net.minecraft.world.level.block.Blocks.CHERRY_LOG;
            leaves = net.minecraft.world.level.block.Blocks.CHERRY_LEAVES;
        } else {
            // 未対応の苗木は汎用オークで代用
            log = net.minecraft.world.level.block.Blocks.OAK_LOG;
            leaves = net.minecraft.world.level.block.Blocks.OAK_LEAVES;
        }
        int size = be.getSize();
        // 必要高さ: 幹4 + 葉1 = 5。苗木位置 y から y+5 が範囲内かつ空であること
        int needHeight = 4;
        for (int dy = 1; dy <= needHeight + 1; dy++) {
            BlockPos p = innerPos.above(dy);
            if (!be.isInRange(p.getX(), p.getY(), p.getZ())) {
                return false;
            }
            BlockState cur = be.getCell(p);
            if (!cur.isAir() && dy <= needHeight) {
                // 幹部分は空でなければならない
                return false;
            }
            if (dy > needHeight) {
                // 葉の最上段は既存ブロックがあっても上書きしない方が安全だが、簡易では空のみ許可
                // ここでは幹以外は葉が既にあっても許容
            }
        }
        // 葉の占有チェック: 幹頂点周辺 3x3x2
        int topY = innerPos.getY() + needHeight;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy <= 0) {
                        continue; // 幹
                    }
                    // 外周角は間引く (丸み)
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                        continue;
                    }
                    BlockPos lp = new BlockPos(innerPos.getX() + dx, topY + dy, innerPos.getZ() + dz);
                    if (!be.isInRange(lp.getX(), lp.getY(), lp.getZ())) {
                        continue;
                    }
                    // 葉は空のみ置く (既存ブロックを壊さない)
                }
            }
        }
        // 幹設置 (苗木位置を含めて上へ)
        for (int dy = 0; dy < needHeight; dy++) {
            BlockPos p = innerPos.above(dy);
            // 苗木位置は幹で上書き
            be.setCell(p, log.defaultBlockState());
        }
        BlockState leavesState = leaves.defaultBlockState();
        // Persistentを true にして落下防止 (可能な場合)
        if (leavesState.hasProperty(BlockStateProperties.PERSISTENT)) {
            leavesState = leavesState.setValue(BlockStateProperties.PERSISTENT, true);
        }
        // 葉設置
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy <= 0) {
                        continue;
                    }
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy == 1) {
                        continue; // 真上は幹が伸びる想定で葉なし
                    }
                    BlockPos lp = new BlockPos(innerPos.getX() + dx, topY + dy, innerPos.getZ() + dz);
                    if (!be.isInRange(lp.getX(), lp.getY(), lp.getZ())) {
                        continue;
                    }
                    if (!be.getCell(lp).isAir()) {
                        continue;
                    }
                    be.setCell(lp, leavesState);
                }
            }
        }
        // 頂点の葉
        BlockPos top = new BlockPos(innerPos.getX(), topY + 1, innerPos.getZ());
        if (be.isInRange(top.getX(), top.getY(), top.getZ()) && be.getCell(top).isAir()) {
            be.setCell(top, leavesState);
        }
        return true;
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
