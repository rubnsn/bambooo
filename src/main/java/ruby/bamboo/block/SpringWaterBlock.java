package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.SpringWaterBlockEntity;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlockEntities;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 温泉水ブロック — LiquidBlock + EntityBlock ハイブリッド。
 * SPRING_LEVEL 0-8で水位管理。LIQUID_BLOCKの LEVELは常に0で自然拡散抑制。
 * BucketPickup で WATER_BUCKET を返すがブロックは残す（Phase D）。
 */
public class SpringWaterBlock extends LiquidBlock implements EntityBlock, BucketPickup {

    public static final IntegerProperty SPRING_LEVEL = IntegerProperty.create("spring_level", 0, 8);

    public SpringWaterBlock(Supplier<? extends FlowingFluid> fluid, Properties props) {
        super(fluid, props
                .mapColor(MapColor.WATER)
                .sound(SoundType.EMPTY)
                .strength(100.0f)
                .noLootTable()
                .noOcclusion()
                .isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(SPRING_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPRING_LEVEL);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int lv = state.getValue(SPRING_LEVEL);
        double h = 0.2 + lv * 0.1;
        if (h > 1.0) h = 1.0;
        if (h < 0.0) h = 0.0;
        return Block.box(0, 0, 0, 16, h * 16, 16);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SpringWaterBlockEntity water) {
                if (water.getParent() == null) {
                    BlockPos foundParent = null;
                    int bestDist = Integer.MAX_VALUE;
                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        BlockPos off = pos.relative(dir);
                        BlockState offState = level.getBlockState(off);
                        if (offState.is(this)) {
                            BlockEntity offBe = level.getBlockEntity(off);
                            if (offBe instanceof SpringWaterBlockEntity offWater) {
                                BlockPos p = offWater.getParent();
                                if (p != null) {
                                    int d = Math.abs(off.getX() - p.getX()) + Math.abs(off.getZ() - p.getZ()) + 1;
                                    if (d < bestDist) {
                                        bestDist = d;
                                        foundParent = p;
                                    }
                                }
                            }
                        }
                    }
                    if (foundParent != null) {
                        water.setParent(foundParent);
                    } else {
                        water.setParent(pos);
                    }
                }
            }
            level.scheduleTick(pos, this, getWaterDelay());
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpringWaterBlockEntity water) {
            if (water.getParent() == null || water.isDead()) {
                SpringWaterBlockEntity.levelDown(level, pos, state);
            } else {
                level.scheduleTick(pos, this, getWaterDelay());
            }
        } else {
            if (state.getValue(SPRING_LEVEL) <= 1) {
                level.removeBlock(pos, false);
            } else {
                level.setBlock(pos, state.setValue(SPRING_LEVEL, state.getValue(SPRING_LEVEL) - 1), 3);
                level.scheduleTick(pos, this, getEvaporationDelay());
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        if (!level.isEmptyBlock(pos.above())) return;
        double x = pos.getX() + random.nextFloat();
        double y = pos.getY() + 1.1D;
        double z = pos.getZ() + random.nextFloat();
        level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpringWaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        if (type != BambooBlockEntities.SPRING_WATER_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SpringWaterBlockEntity>) (lvl, p, s, be) -> SpringWaterBlockEntity.tick(lvl, p, s, be);
    }

    // ===== BucketPickup (Phase D) — 水バケツを返すがブロックは残す =====
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

    private int getWaterDelay() {
        try { return SpringConfig.COMMON.waterTickDelay.get(); } catch (Exception e) { return 20; }
    }

    private int getEvaporationDelay() {
        try { return SpringConfig.COMMON.evaporationDelay.get(); } catch (Exception e) { return 30; }
    }
}
