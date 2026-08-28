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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
 * 温泉水ブロック — 水系継承で PARENT_DIR のみ独自。親は SpringBlock（源泉）に集約。
 * SPRING_LEVEL 0-8で水位管理。LIQUID_BLOCKの LEVELは常に0で自然拡散抑制。
 * BucketPickup で WATER_BUCKET を返すがブロックは残す。
 * BE/BREは不要（Stateで親方向を保持し、色は源泉の COLOR を辿る）。
 */
public class SpringWaterBlock extends LiquidBlock implements BucketPickup {

    public static final IntegerProperty SPRING_LEVEL = IntegerProperty.create("spring_level", 0, 8);
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
        this.registerDefaultState(this.stateDefinition.any().setValue(SPRING_LEVEL, 0).setValue(PARENT_DIR, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPRING_LEVEL, PARENT_DIR);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
        int lv = state.getValue(SPRING_LEVEL);
        if (lv <= 0) return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
        if (lv >= 8) {
            try {
                return ruby.bamboo.BambooMod.SPRING_WATER_SOURCE.get().defaultFluidState();
            } catch (Exception e) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }
        }
        try {
            // FlowingFluid.LEVEL は LEVEL_FLOWING (1-8)。LiquidBlock.LEVEL(0-15) とは別物のため FlowingFluid.LEVEL を使う
            var flowing = (net.minecraft.world.level.material.FlowingFluid) ruby.bamboo.BambooMod.SPRING_WATER_FLOWING.get();
            return flowing.getFlowing(lv, false);
        } catch (Exception e) {
            try {
                return ruby.bamboo.BambooMod.SPRING_WATER_FLOWING.get().defaultFluidState()
                        .setValue(net.minecraft.world.level.material.FlowingFluid.LEVEL, lv);
            } catch (Exception e2) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }
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
            // 黒はリセット的に DEFAULT に
            if (dyeItem.getDyeColor() == net.minecraft.world.item.DyeColor.BLACK) dyeCol = SpringColor.DEFAULT;
            level.setBlock(source, srcState.setValue(SpringBlock.COLOR, dyeCol), 3);
            stack.shrink(1);
            if (stack.isEmpty()) itemEntity.discard(); else itemEntity.setItem(stack);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // 1枚目の親方向決定：源泉直上なら DOWN、周囲の温泉水があれば最も近い源泉への方向を継承
            Direction dir = findBestParentDir(level, pos);
            BlockState ns = state.setValue(PARENT_DIR, dir);
            if (ns != state) level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, this, getWaterDelay());
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // 周囲変化で親が途切れたら蒸発判定をスケジュール
        if (state.hasProperty(PARENT_DIR) && level instanceof Level lvl && !lvl.isClientSide) {
            BlockPos src = findSource(lvl, pos, state, 32);
            if (src == null) {
                // 親不達 → 蒸発予約
                level.scheduleTick(pos, this, getEvaporationDelay());
            }
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 蒸発チェック：親が無いか源泉がOFFなら levelDown
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
        // 高さがmax未満なら自分を levelUp（均一化の代替：最も低いものが自分なら自分が上がる）
        int lv = state.getValue(SPRING_LEVEL);
        int max = getMaxLevel();
        if (lv < max) {
            // 周囲で最も低いものを探す（自分+隣接）
            BlockPos bestPos = pos;
            int bestLv = lv;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos off = pos.relative(d);
                BlockState offState = level.getBlockState(off);
                if (!offState.is(this)) continue;
                // 距離チェック：親からのマンハッタンが32超なら蒸発対象だがここでは無視
                int offLv = offState.getValue(SPRING_LEVEL);
                if (offLv < bestLv) {
                    bestLv = offLv;
                    bestPos = off;
                }
            }
            // 最も低いものがmax未満なら +1
            if (bestLv < max) {
                BlockState bestState = level.getBlockState(bestPos);
                if (bestState.is(this)) {
                    levelUp(level, bestPos, bestState);
                }
            }
            // 自分がまだ満水でなければ再スケジュールして継続
            level.scheduleTick(pos, this, getWaterDelay());
            return;
        }
        // 満水なら水平拡散
        trySpread(level, pos, state, src);
        // 下注ぎ
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(this)) {
            int belowLv = belowState.getValue(SPRING_LEVEL);
            if (belowLv < max) levelUp(level, below, belowState);
        } else {
            // 下が空気/置換可能で親距離内なら下へも拡散は trySpread で水平のみなので下への自然落下はバニラ流体に任せず、必要ならここで設置
        }
        // 安定時は休止（周囲変化があれば updateShape で再開）
        // 満水で安定しているなら再スケジュールしない
    }

    private void trySpread(ServerLevel level, BlockPos pos, BlockState state, BlockPos src) {
        int lv = state.getValue(SPRING_LEVEL);
        if (lv != getMaxLevel()) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos cand = pos.relative(dir);
            BlockState candState = level.getBlockState(cand);
            if (candState.is(this)) continue;
            boolean empty = level.isEmptyBlock(cand) || candState.canBeReplaced();
            if (!empty) continue;
            int dist = Math.abs(cand.getX() - src.getX()) + Math.abs(cand.getZ() - src.getZ());
            if (dist > getMaxRadius()) {
                // 32超は冷めて通常水に委譲
                if (level.isEmptyBlock(cand)) level.setBlock(cand, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
                continue;
            }
            // 親方向は自分への方向
            Direction toParent = dir.getOpposite();
            BlockState ns = this.defaultBlockState().setValue(SPRING_LEVEL, 1).setValue(PARENT_DIR, toParent);
            level.setBlock(cand, ns, 3);
            level.scheduleTick(cand, this, getWaterDelay());
            break;
        }
    }

    /** 親方向を辿って源泉を探す（最大32ステップ、高さ含む） */
    @Nullable
    public static BlockPos findSource(Level level, BlockPos pos, BlockState state, int limit) {
        BlockPos cur = pos;
        BlockState curState = state;
        for (int i = 0; i < limit; i++) {
            if (!curState.is(BambooBlocks.SPRING_WATER.get())) {
                // 現在位置が水でない場合は源泉直上かどうか
                BlockPos below = cur.below();
                BlockState belowState = level.getBlockState(below);
                if (belowState.getBlock() instanceof SpringBlock) return below;
                // 親方向があれば辿る
                if (!curState.hasProperty(PARENT_DIR)) return null;
                Direction d = curState.getValue(PARENT_DIR);
                cur = cur.relative(d);
                curState = level.getBlockState(cur);
                continue;
            }
            // 水の場合：PARENT_DIR を辿る
            if (!curState.hasProperty(PARENT_DIR)) return null;
            Direction dir = curState.getValue(PARENT_DIR);
            BlockPos next = cur.relative(dir);
            BlockState nextState = level.getBlockState(next);
            // 次が源泉ならそれが親
            if (nextState.getBlock() instanceof SpringBlock) {
                return next;
            }
            // 次が水なら継続
            if (nextState.is(BambooBlocks.SPRING_WATER.get())) {
                cur = next;
                curState = nextState;
                continue;
            }
            // 次が空気等の場合はその先に水があるかも知れないが、親は必ず隣接するはずなので不達
            return null;
        }
        return null;
    }

    private Direction findBestParentDir(Level level, BlockPos pos) {
        // 源泉直上なら DOWN
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.getBlock() instanceof SpringBlock) return Direction.DOWN;
        // 周囲の水から最も近い源泉を持つ方向を探す
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
        // 見つからなければ自分が親（最初の水）
        if (bestSrc == null) return Direction.UP;
        return bestDir;
    }

    public static boolean levelUp(Level level, BlockPos pos, BlockState state) {
        int max = getMaxLevel();
        int lv = state.getValue(SPRING_LEVEL);
        if (lv < max) {
            BlockState ns = state.setValue(SPRING_LEVEL, lv + 1);
            level.setBlock(pos, ns, 3);
            level.scheduleTick(pos, BambooBlocks.SPRING_WATER.get(), getWaterDelayStatic());
            return true;
        }
        return false;
    }

    public static void levelDown(Level level, BlockPos pos, BlockState state) {
        int lv = state.getValue(SPRING_LEVEL);
        if (lv <= 1) {
            // 32超で冷めた場合は通常水に
            BlockPos src = null;
            if (state.hasProperty(PARENT_DIR)) src = findSource(level, pos, state, 32);
            if (src != null) {
                int dist = Math.abs(pos.getX() - src.getX()) + Math.abs(pos.getZ() - src.getZ());
                if (dist > getMaxRadiusStatic()) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
                    return;
                }
            }
            level.removeBlock(pos, false);
        } else {
            BlockState ns = state.setValue(SPRING_LEVEL, lv - 1);
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

    // BucketPickup
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
        return ((r1 * r2 / 255) << 16) | ((g1 * g2 / 255) << 8) | (b1 * b2 / 255);
    }

    /** BlockAndTintGetter 版の findSource（BlockColors 用） */
    @Nullable
    public static BlockPos findSource(net.minecraft.world.level.BlockAndTintGetter getter, BlockPos pos, BlockState state, int limit) {
        if (getter instanceof Level lvl) return findSource(lvl, pos, state, limit);
        return null;
    }
}
