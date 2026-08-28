package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlocks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 温泉水ブロック — LiquidBlock 準拠で Water の LEVEL(0高/7低)を使用。SPRING_LEVELは廃止。
 * PARENT_DIR で親を辿り、源泉からの拡散・水位均一化・蒸発を制御。水没対応あり。
 */
public class SpringWaterBlock extends LiquidBlock implements BucketPickup {

    public static final EnumProperty<Direction> PARENT_DIR = EnumProperty.create("parent_dir", Direction.class);

    public SpringWaterBlock(Supplier<? extends FlowingFluid> fluid, Properties props) {
        super(fluid, props
                .mapColor(MapColor.WATER)
                .sound(SoundType.EMPTY)
                .strength(100.0f)
                .noLootTable()
                .noOcclusion()
                .noCollission()
                .liquid()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false)
                .isRedstoneConductor((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(PARENT_DIR, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PARENT_DIR);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        int lv = state.getValue(LEVEL);
        if (lv <= 0) return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
        // Block LEVEL 1低(流動1) ->7高(流動7) を Fluid の高さに直結させるため直接マッピング
        // 7は満水に近い高さ、1は浅い
        try {
            if (lv >= 7) {
                // 7高は SOURCE に近い高さで見せるため SOURCE を返す（流動7でも可だが SOURCE の方が高く見える）
                return ruby.bamboo.BambooMod.SPRING_WATER_SOURCE.get().defaultFluidState();
            }
            return ruby.bamboo.BambooMod.SPRING_WATER_FLOWING.get().getFlowing(lv, false);
        } catch (Exception e) {
            return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
        }
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) return;
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            var stack = itemEntity.getItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof DyeItem dyeItem)) return;
            BlockPos source = findSource(level, pos, state, 32);
            if (source == null) return;
            BlockState srcState = level.getBlockState(source);
            if (!(srcState.getBlock() instanceof SpringBlock)) return;
            SpringColor cur = srcState.getValue(SpringBlock.COLOR);
            SpringColor dyeCol = SpringColor.fromDye(dyeItem.getDyeColor());
            if (dyeCol == null || cur == dyeCol) return;
            if (dyeItem.getDyeColor() == net.minecraft.world.item.DyeColor.BLACK) dyeCol = SpringColor.DEFAULT;
            level.setBlock(source, srcState.setValue(SpringBlock.COLOR, dyeCol), 3);
            // 染料変更を全連結水に即時反映させるため、近傍の温泉水チャンクを再送（sakuraのgetFluidToList相当のBFS）
            try {
                var waters = collectConnectedWaters(level, source.above(), 250);
                for (BlockPos p : waters) {
                    var s = level.getBlockState(p);
                    if (s.getBlock() instanceof SpringWaterBlock) {
                        // 同一stateで再送してクライアントの流体メッシュ再構築を促す
                        level.sendBlockUpdated(p, s, s, 3);
                    } else {
                        // 水没の場合、その位置のブロックを再送
                        var fs = level.getFluidState(p);
                        if (fs.getType() == ruby.bamboo.BambooMod.SPRING_WATER_SOURCE.get() || fs.getType() == ruby.bamboo.BambooMod.SPRING_WATER_FLOWING.get()) {
                            var bs = level.getBlockState(p);
                            level.sendBlockUpdated(p, bs, bs, 3);
                        }
                    }
                }
                level.sendBlockUpdated(source, srcState, level.getBlockState(source), 3);
            } catch (Exception ignored) {}
            stack.shrink(1);
            if (stack.isEmpty()) itemEntity.discard(); else itemEntity.setItem(stack);
        }
    }

    /** sakura SpringSpawner.getFluidToList 相当: 源泉からの連結温泉水(流体)をBFSで収集 */
    private static java.util.Set<BlockPos> collectConnectedWaters(Level level, BlockPos start, int limit) {
        java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        if (level.getBlockState(start).getBlock() instanceof SpringWaterBlock || isSpringFluid(level, start)) queue.add(start);
        else {
            // startが空気の場合、周囲から最初の水を探す
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                BlockPos n = start.relative(d);
                if (level.getBlockState(n).getBlock() instanceof SpringWaterBlock || isSpringFluid(level, n)) queue.add(n);
            }
        }
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos cur = queue.poll();
            if (!found.add(cur)) continue;
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                BlockPos nxt = cur.relative(d);
                if (found.contains(nxt)) continue;
                if (nxt.distManhattan(start) > 64) continue;
                boolean isBlock = level.getBlockState(nxt).getBlock() instanceof SpringWaterBlock;
                boolean isFluid = isSpringFluid(level, nxt);
                if ((isBlock || isFluid) && found.size() < limit) queue.add(nxt);
            }
        }
        return found;
    }

    private static boolean isSpringFluid(Level level, BlockPos p) {
        var f = level.getFluidState(p);
        return f.getType() == ruby.bamboo.BambooMod.SPRING_WATER_SOURCE.get() || f.getType() == ruby.bamboo.BambooMod.SPRING_WATER_FLOWING.get();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        // 自然流体の拡散を抑止し自前制御のみにするため super の Fluid tick 予約は呼ばない
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, getWaterDelay());
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.hasProperty(PARENT_DIR) && level instanceof Level lvl && !lvl.isClientSide) {
            BlockPos src = findSource(lvl, pos, state, 32);
            if (src == null) {
                level.scheduleTick(pos, this, getEvaporationDelay());
            } else {
                // 新たに拡散可能な空間ができたら再スケジュール（上面以外）— lv に依らず起床させ拡散を優先
                // 水没は固体として拡散対象外（バニラ水フォールバック廃止）
                if (dir != Direction.UP && !neighborState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    boolean canSpread = level.isEmptyBlock(neighborPos) || neighborState.canBeReplaced();
                    if (canSpread) {
                        int dist = Math.abs(neighborPos.getX() - src.getX()) + Math.abs(neighborPos.getZ() - src.getZ());
                        if (dist <= getMaxRadius()) {
                            level.scheduleTick(pos, this, getWaterDelay());
                        }
                    }
                }
            }
        }
        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide) {
            BlockPos src = findSource(level, pos, state, 32);
            if (src != null) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.hasProperty(BlockStateProperties.WATERLOGGED)) return;
                boolean isNeighborEmpty = level.isEmptyBlock(neighborPos) || neighborState.canBeReplaced();
                if (isNeighborEmpty) {
                    // lv 値に依らず起床 — tick 側で拡散可否(lv>1)を判定し拡散を優先する
                    int dist = Math.abs(neighborPos.getX() - src.getX()) + Math.abs(neighborPos.getZ() - src.getZ());
                    if (dist <= getMaxRadius() || neighborPos.equals(src.above())) {
                        level.scheduleTick(pos, this, getWaterDelay());
                    }
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos src = findSource(level, pos, state, 32);
        if (src == null) {
            levelDown(level, pos, state);
            return;
        }
        BlockState srcState = level.getBlockState(src);
        if (!(srcState.getBlock() instanceof SpringBlock) || !srcState.getValue(SpringBlock.ACTIVE)) {
            levelDown(level, pos, state);
            return;
        }
        int lv = state.getValue(LEVEL);
        // 1低(最小) ->7高(満水)で管理。0はバニラSOURCEと被るため不使用だが、誤って0が来たら1として扱う
        if (lv == 0) lv = 1;
        int maxHere = getMaxLevelAt(level, pos);
        // 拡散を最優先で探索（上限 7 も含めて実行。1は拡散不可のため trySpread 内で弾く）
        boolean didSpread = false;
        if (lv > 1) {
            didSpread = trySpread(level, pos, state, src);
        }
        if (lv < maxHere) {
            // 最もレベルが低い(低水位)ものを見つけて持ち上げる（均一化）— 距離上限を考慮
            BlockPos bestPos = pos;
            int bestLv = lv;
            int bestMax = maxHere;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos off = pos.relative(d);
                BlockState offState = level.getBlockState(off);
                if (!offState.is(this)) continue;
                int offLv = offState.getValue(LEVEL);
                if (offLv == 0) offLv = 1;
                int offMax = getMaxLevelAt(level, off);
                if (offLv >= offMax) continue;
                if (offLv < bestLv) {
                    bestLv = offLv;
                    bestPos = off;
                    bestMax = offMax;
                }
            }
            if (bestLv < bestMax) {
                BlockState bestState = level.getBlockState(bestPos);
                if (bestState.is(this)) {
                    // 0の補正
                    if (bestState.getValue(LEVEL) == 0) {
                        bestState = bestState.setValue(LEVEL, 1);
                        level.setBlock(bestPos, bestState, 3);
                    }
                    levelUp(level, bestPos, bestState);
                }
            } else {
                // 自身が最も低い場合は自身を上昇
                levelUp(level, pos, state);
            }
            level.scheduleTick(pos, this, getWaterDelay());
            return;
        }
        // 満水時: 下への注ぎ + 拡散で穴が空いても再拡散できるよう再スケジュール
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(this)) {
            int belowLv = belowState.getValue(LEVEL);
            if (belowLv > 0) levelUp(level, below, belowState);
        }
        // 満水でも周囲が空けば trySpread が次tickで埋められるよう常時再スケジュール
        level.scheduleTick(pos, this, getWaterDelay());
    }

    private boolean trySpread(ServerLevel level, BlockPos pos, BlockState state, BlockPos src) {
        int lv = state.getValue(LEVEL);
        if (lv == 0) lv = 1;
        if (lv <= 1) return false; // 最低(1)では拡散不可、2以上なら即拡散
        boolean placed = false;
        // 上面以外へ同時拡散（水平4方向 + 真下）— 拡散を均一化より先に行う
        // 水没は行わずフェンス等は固体として拡散をブロック（バニラ水フォールバック廃止）
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            BlockPos cand = pos.relative(dir);
            BlockState candState = level.getBlockState(cand);
            if (candState.is(this)) continue;
            if (candState.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
            boolean empty = level.isEmptyBlock(cand) || candState.canBeReplaced();
            if (!empty) continue;
            int dist = Math.abs(cand.getX() - src.getX()) + Math.abs(cand.getZ() - src.getZ());
            if (dist > getMaxRadius()) continue;
            Direction toParent = dir.getOpposite();
            BlockState ns = this.defaultBlockState().setValue(LEVEL, 1).setValue(PARENT_DIR, toParent);
            level.setBlock(cand, ns, 3);
            level.scheduleTick(cand, this, getWaterDelay());
            placed = true;
        }
        return placed;
    }

    /** 親方向を辿って源泉を探す */
    @Nullable
    public static BlockPos findSource(Level level, BlockPos pos, BlockState state, int limit) {
        BlockPos cur = pos;
        BlockState curState = state;
        for (int i = 0; i < limit; i++) {
            if (!curState.is(BambooBlocks.SPRING_WATER.get())) {
                BlockPos below = cur.below();
                BlockState belowState = level.getBlockState(below);
                if (belowState.getBlock() instanceof SpringBlock) return below;
                if (!curState.hasProperty(PARENT_DIR)) return null;
                Direction d = curState.getValue(PARENT_DIR);
                cur = cur.relative(d);
                curState = level.getBlockState(cur);
                continue;
            }
            if (!curState.hasProperty(PARENT_DIR)) return null;
            Direction dir = curState.getValue(PARENT_DIR);
            BlockPos next = cur.relative(dir);
            BlockState nextState = level.getBlockState(next);
            if (nextState.getBlock() instanceof SpringBlock) {
                return next;
            }
            if (nextState.is(BambooBlocks.SPRING_WATER.get())) {
                cur = next;
                curState = nextState;
                continue;
            }
            return null;
        }
        return null;
    }

    private Direction findBestParentDir(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.getBlock() instanceof SpringBlock) return Direction.DOWN;
        Direction bestDir = Direction.UP;
        int bestDist = Integer.MAX_VALUE;
        BlockPos bestSrc = null;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            BlockPos off = pos.relative(dir);
            BlockState offState = level.getBlockState(off);
            if (!offState.is(this)) continue;
            BlockPos src = findSource(level, off, offState, 32);
            if (src == null) continue;
            int d = Math.abs(pos.getX() - src.getX()) + Math.abs(pos.getZ() - src.getZ()) + Math.abs(pos.getY() - src.getY());
            if (d < bestDist) {
                bestDist = d;
                bestDir = dir;
                bestSrc = src;
            }
        }
        if (bestSrc == null) return Direction.UP;
        return bestDir;
    }

    /** 距離に応じた水位上限: 0で7、32で1に線形減少 */
    public static int getMaxLevelForDist(int dist) {
        int maxR = getMaxRadiusStatic();
        if (dist <= 0) return 7;
        if (dist >= maxR) return 1;
        return Math.max(1, 7 - (dist * 6) / maxR);
    }

    public static int getMaxLevelAt(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        BlockPos src = findSource(level, pos, st, 32);
        if (src == null) return 7;
        int dist = Math.abs(pos.getX() - src.getX()) + Math.abs(pos.getZ() - src.getZ());
        return getMaxLevelForDist(dist);
    }

    public static boolean levelUp(Level level, BlockPos pos, BlockState state) {
        int lv = state.getValue(LEVEL);
        if (lv == 0) lv = 1;
        int max = getMaxLevelAt(level, pos);
        // 距離上限に近づくほど上限が下がり、32地点は1で停止
        if (lv < max) {
            int next = lv + 1;
            if (next > max) next = max;
            if (next > 7) next = 7;
            BlockState ns = state.setValue(LEVEL, next);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getWaterDelayStatic());
            return true;
        }
        return false;
    }

    public static void levelDown(Level level, BlockPos pos, BlockState state) {
        int lv = state.getValue(LEVEL);
        // 水没ブロックの場合は WATERLOGGED を false にする（バケツ汲み出しと同様）
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, Boolean.FALSE), 3);
            return;
        }
        if (lv <= 1) {
            // 液体の消滅は removeBlock ではなく AIR 置換（pickupBlock と同様、flag 11 で更新）で水没と両立
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
        } else {
            BlockState ns = state.setValue(LEVEL, lv - 1);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getEvaporationDelayStatic());
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(20) != 0) return;
        if (!level.isEmptyBlock(pos.above())) return;
        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
        double y = pos.getY() + 1.05D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, x, y, z, 0.0D, 0.03D, 0.0D);
    }

    @Override
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    @Override
    public Optional<SoundEvent> getPickupSound(BlockState state) {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    private int getWaterDelay() { try { return SpringConfig.COMMON.waterTickDelay.get(); } catch (Exception e){ return 20; } }
    private static int getWaterDelayStatic() { try { return SpringConfig.COMMON.waterTickDelay.get(); } catch (Exception e){ return 20; } }
    private int getEvaporationDelay() { try { return SpringConfig.COMMON.evaporationDelay.get(); } catch (Exception e){ return 30; } }
    private static int getEvaporationDelayStatic() { try { return SpringConfig.COMMON.evaporationDelay.get(); } catch (Exception e){ return 30; } }
    private static int getMaxLevel() { try { return SpringConfig.COMMON.maxLevel.get(); } catch (Exception e){ return 8; } }
    private static int getMaxRadius() { try { return SpringConfig.COMMON.maxSpreadRadius.get(); } catch (Exception e){ return 32; } }
    private static int getMaxRadiusStatic() { try { return SpringConfig.COMMON.maxSpreadRadius.get(); } catch (Exception e){ return 32; } }

    public static int multiplyColor(int base, int tint) {
        int r1 = (base >> 16) & 0xFF, g1 = (base >> 8) & 0xFF, b1 = base & 0xFF;
        int r2 = (tint >> 16) & 0xFF, g2 = (tint >> 8) & 0xFF, b2 = tint & 0xFF;
        return 0xFF000000 | ((r1 * r2 / 255) << 16) | ((g1 * g2 / 255) << 8) | (b1 * b2 / 255);
    }

    @Nullable
    public static BlockPos findSource(net.minecraft.world.level.BlockAndTintGetter getter, BlockPos pos, BlockState state, int limit) {
        if (getter instanceof Level lvl) return findSource(lvl, pos, state, limit);
        // クライアントの ChunkRenderCache 等でも辿れるよう BlockAndTintGetter 版を実装
        BlockPos cur = pos;
        BlockState curState = state;
        for (int i = 0; i < limit; i++) {
            if (!curState.is(BambooBlocks.SPRING_WATER.get())) {
                BlockPos below = cur.below();
                BlockState belowState = getter.getBlockState(below);
                if (belowState.getBlock() instanceof SpringBlock) return below;
                if (!curState.hasProperty(PARENT_DIR)) return null;
                Direction d = curState.getValue(PARENT_DIR);
                cur = cur.relative(d);
                curState = getter.getBlockState(cur);
                continue;
            }
            if (!curState.hasProperty(PARENT_DIR)) return null;
            Direction dir = curState.getValue(PARENT_DIR);
            BlockPos next = cur.relative(dir);
            BlockState nextState = getter.getBlockState(next);
            if (nextState.getBlock() instanceof SpringBlock) return next;
            if (nextState.is(BambooBlocks.SPRING_WATER.get())) {
                cur = next;
                curState = nextState;
                continue;
            }
            return null;
        }
        return null;
    }
}
