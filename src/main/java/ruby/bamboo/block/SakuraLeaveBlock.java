package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooParticles;

/**
 * 桜の葉。旧 SakuraLeave (1.10.2) の移植。
 * <p>
 * 旧版は常時光レベル12相当の発光 + 非透明扱いだったため、1.20.1では
 * lightLevel(9) を与え、randomTick で周囲に苗木をドロップする挙動を再現した。
 */
public class SakuraLeaveBlock extends LeavesBlock {

    /** 旧 EnumLeave.PINK の花びら色 (0xFFC5CC)。現行は単一ピンク葉のため固定 */
    private static final int PETAL_COLOR = 0xFFC5CC;

    public SakuraLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    /**
     * 花びらパーティクル (旧 SakuraLeave#randomDisplayTick 相当)。
     * 1/100 の確率で直下が空気なら発生。
     */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        if (rand.nextInt(100) != 0) {
            return;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            double x = pos.getX() + rand.nextDouble();
            double y = pos.getY();
            double z = pos.getZ() + rand.nextDouble();
            // 桜属は花びらテクスチャ1を使用 (旧 getPetal 相当)
            level.addParticle(BambooParticles.PETAL_1.get(),
                    x, y, z,
                    ((PETAL_COLOR >> 16) & 0xff) / 255.0,
                    ((PETAL_COLOR >> 8) & 0xff) / 255.0,
                    (PETAL_COLOR & 0xff) / 255.0);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand) {
        // 旧 updateTick: 葉の下の地面にたまに苗木が生える (確率1/20)
        super.randomTick(state, level, pos, rand);
        if (!level.isClientSide() && rand.nextInt(20) == 0) {
            BlockPos below = pos.below();
            if (level.isEmptyBlock(below)
                    && (level.getBlockState(below.below()).is(Blocks.DIRT)
                            || level.getBlockState(below.below()).is(Blocks.GRASS_BLOCK))) {
                level.setBlock(below,
                        BambooBlocks.SAKURA_SAPLING.get().defaultBlockState().setValue(SaplingBlock.STAGE, 0),
                        3);
            }
        }
    }
}
