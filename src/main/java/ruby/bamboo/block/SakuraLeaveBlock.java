package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.client.particle.PetalWind;
import ruby.bamboo.core.init.BambooParticles;

/**
 * 桜の葉。旧 SakuraLeave (1.10.2) の移植。
 * <p>
 * 旧版は常時光レベル12相当の発光 + 非透明扱いだったため、1.20.1では
 * lightLevel(9) を与える。旧 randomTick での苗木自動生成は無限増殖を招くためオミット
 * （苗木は破壊時の loot_table でのみ入手）。
 */
public class SakuraLeaveBlock extends LeavesBlock {

    /** 旧 EnumLeave.PINK の花びら色 (0xFFC5CC)。現行は単一ピンク葉のため固定 */
    public static final int PETAL_COLOR = 0xFFC5CC;

    public SakuraLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    /**
     * 花びらパーティクル (旧 SakuraLeave#randomDisplayTick 相当)。
     * 通常 1/100、突風時は PetalWind で密になる + たまに1粒おかわり。
     * 直下が空気のときのみ発生。
     */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        if (rand.nextInt(PetalWind.spawnChance(100, level)) != 0) {
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
            if (PetalWind.spawnExtra(rand, level)) {
                level.addParticle(BambooParticles.PETAL_1.get(),
                        pos.getX() + rand.nextDouble(), y, pos.getZ() + rand.nextDouble(),
                        ((PETAL_COLOR >> 16) & 0xff) / 255.0,
                        ((PETAL_COLOR >> 8) & 0xff) / 255.0,
                        (PETAL_COLOR & 0xff) / 255.0);
            }
        }
    }
}
