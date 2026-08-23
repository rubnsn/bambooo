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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * たけのこ。旧 BambooShoot (1.10.2) の移植。
 * <p>
 * ランダムティックで光レベル7以上かつ頭上空きなら竹に成長する。
 * 骨粉でも成長 (IGrowable → BonemealableBlock)。
 * 旧版の META (0-1) は実質使われていないため廃止し単一状態とした。
 */
public class BambooShootBlock extends BushBlock implements net.minecraft.world.level.block.BonemealableBlock {

    /** 旧 BLOCK_AABB (0.3,0,0.3)-(0.7,0.5,0.7) */
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 8, 11);

    private static final float GROW_PROBABILITY = 0.125F;
    private static final float GROW_PROBABILITY_RAIN = 0.25F;

    public BambooShootBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        // 旧 canPlaceBlockAt: 草/土/耕地のみ
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.FARMLAND);
    }

    /**
     * 竹へ成長できるか (旧 canChildGrow): 頭上空き + 人工光7以上。
     */
    private boolean canGrowToBamboo(ServerLevel level, BlockPos pos) {
        if (!level.isEmptyBlock(pos.above())) {
            return false;
        }
        return level.getMaxLocalRawBrightness(pos) > 7;
    }

    private void tryBambooGrowth(ServerLevel level, BlockPos pos, float probability) {
        if (!level.isClientSide() && level.random.nextFloat() < probability && this.canGrowToBamboo(level, pos)) {
            level.setBlock(pos, BambooBlocks.BAMBOO.get().defaultBlockState(), 3);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        tryBambooGrowth(level, pos, level.isRainingAt(pos.above()) ? GROW_PROBABILITY_RAIN : GROW_PROBABILITY);
    }

    // ===== BonemealableBlock (旧 IGrowable) =====

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(net.minecraft.world.level.Level level, RandomSource rand, BlockPos pos,
            BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource rand, BlockPos pos, BlockState state) {
        tryBambooGrowth(level, pos, 0.75F);
    }
}
