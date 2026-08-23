package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 竹。旧 Bamboo (1.10.2) の移植。
 * <p>
 * 旧版は LENGTH (meta 0-15) を持ち、上へ伸びる・周囲にたけのこを増やす挙動だった。
 * 1.20.1では LENGTH → age(0-15) property として移植。
 * 下が竹なら維持、土/草/耕地の上にのみ設置可能。
 */
public class BambooBlock extends BushBlock {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 15);

    /** 旧 BLOCK_AABB (0.125,0,0.125)-(0.875,1,0.875) */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    private static final float GROW_PROBABILITY = 0.125F;
    private static final float GROW_PROBABILITY_RAIN = 0.25F;

    public BambooBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 10));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.FARMLAND);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        // 自身の下 or 土/草/耕地の上 (旧 canBlockStay)
        return level.getBlockState(pos.below()).getBlock() == this || super.canSurvive(state, level, pos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        tryGrow(level, pos, state, rand, level.isRainingAt(pos.above()) ? GROW_PROBABILITY_RAIN : GROW_PROBABILITY);
    }

    private void tryGrow(ServerLevel level, BlockPos pos, BlockState state, RandomSource rand, float probability) {
        if (!level.isEmptyBlock(pos.above())) {
            return;
        }
        if (rand.nextFloat() >= probability) {
            return;
        }
        int meta = state.getValue(AGE);
        if (meta > 0) {
            // 上へ1段成長、age を1減らす (旧 growBamboo)
            level.setBlock(pos.above(), state.setValue(AGE, meta - 1), 3);
        } else {
            // 地際 (age=0): 周囲にたけのこを発生させる可能性 (旧 tryChildSpawn)
            tryChildSpawn(level, pos, rand);
        }
    }

    /**
     * 地面まで下りて、その周囲1マスにたけのこを発生させる (旧 tryChildSpawn 相当)。\n     * 地面は土/草/耕地に置き換わる。\n     */
    private void tryChildSpawn(ServerLevel level, BlockPos basePos, RandomSource rand) {
        BlockPos ground = basePos;
        // 地面(土系)を探す
        while (mayPlaceOn(level.getBlockState(ground), level, ground)) {
            ground = ground.below();
        }
        ground = ground.above();

        for (int i = 0; i < 8; i++) {
            int dx = rand.nextInt(3) - 1;
            int dy = rand.nextInt(3) - 1;
            int dz = rand.nextInt(3) - 1;
            BlockPos target = ground.offset(dx, dy - 1, dz);
            BlockPos shootPos = target.above();
            if (!level.isEmptyBlock(target) || !level.isEmptyBlock(shootPos)) {
                continue;
            }
            BlockState below = level.getBlockState(target);
            if (!mayPlaceOn(below, level, target)) {
                continue;
            }
            // 天候・耕地で確率変動 (旧 canChildSpawn)
            float threshold = level.isRainingAt(shootPos) ? 0.4F : below.is(Blocks.FARMLAND) ? 0.25F : 0.1F;
            if (rand.nextFloat() < threshold) {
                level.setBlock(target, Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(shootPos, BambooBlocks.BAMBOO_SHOOT.get().defaultBlockState(), 3);
            }
        }
    }
}
