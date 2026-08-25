package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.EntityBlock;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.block.entity.SpringWaterBlockEntity;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlockEntities;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 温泉水ブロック — LiquidBlock + EntityBlock ハイブリッド。
 * SPRING_LEVEL 0-8で水位管理。LIQUID_BLOCKの LEVELは常に0で自然拡散抑制。
 */
public class SpringWaterBlock extends LiquidBlock implements EntityBlock {

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
        // 高さ 0.2 + LEVEL*0.1 (spec)
        double h = 0.2 + lv * 0.1;
        if (h > 1.0) h = 1.0;
        if (h < 0.0) h = 0.0;
        return Block.box(0, 0, 0, 16, h * 16, 16);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            // BEが生成された後に親決定
            // onPlace時はまだBEがnullのことがあるため、BE生成を待ってから親決定はBE側のonLoad? 簡易: ここでBEを取得して初期化
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SpringWaterBlockEntity water) {
                if (water.getParent() == null) {
                    // 周囲から親を引き継ぐ
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
        // BE側のtickに委譲するため、ここでは再スケジュールのみ行わない
        // BEが isDead なら levelDown はBE側で処理される
        // ここでBEが存在しない場合のフォールバックとして levelDown も行うが BE優先
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SpringWaterBlockEntity water) {
            // BE tick will handle; ensure scheduled if not dead
            if (water.getParent() == null || water.isDead()) {
                SpringWaterBlockEntity.levelDown(level, pos, state);
            } else {
                // 正常時は再スケジュール (Phase B minimal: 何もしないが再スケジュールはBE側がしないためここで継続)
                // ただしBEが正常でも水位維持のために定期的にtickさせたい
                level.scheduleTick(pos, this, getWaterDelay());
            }
        } else {
            // BE不在フォールバック: 徐々に消える
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

    // ===== EntityBlock =====
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

    private int getWaterDelay() {
        try { return SpringConfig.COMMON.waterTickDelay.get(); } catch (Exception e) { return 20; }
    }

    private int getEvaporationDelay() {
        try { return SpringConfig.COMMON.evaporationDelay.get(); } catch (Exception e) { return 30; }
    }
}
