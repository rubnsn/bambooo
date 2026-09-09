package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.client.particle.PetalWind;
import ruby.bamboo.core.init.BambooParticles;

/**
 * カエデの葉 (SakuraBlocks MAPLE_LEAVE 相当)。
 * SakuraLeaveのREDバリアント (0xC80010, petal_2, MASS 1.2F) を独立ブロック化。
 * テクスチャは broadleaf.png を BlockColor で乗算 (0xC80010)。
 */
public class MapleLeaveBlock extends LeavesBlock {

    /** カエデの花びら/葉色 (旧 EnumLeave.RED) */
    public static final int PETAL_COLOR = 0xC80010;

    public MapleLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        // 通常 1/100、突風時は PetalWind で密になる + たまに1粒おかわり
        if (rand.nextInt(PetalWind.spawnChance(100, level)) != 0) {
            return;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            double x = pos.getX() + rand.nextDouble();
            double y = pos.getY();
            double z = pos.getZ() + rand.nextDouble();
            level.addParticle(BambooParticles.PETAL_2.get(),
                    x, y, z,
                    ((PETAL_COLOR >> 16) & 0xff) / 255.0,
                    ((PETAL_COLOR >> 8) & 0xff) / 255.0,
                    (PETAL_COLOR & 0xff) / 255.0);
            if (PetalWind.spawnExtra(rand, level)) {
                level.addParticle(BambooParticles.PETAL_2.get(),
                        pos.getX() + rand.nextDouble(), y, pos.getZ() + rand.nextDouble(),
                        ((PETAL_COLOR >> 16) & 0xff) / 255.0,
                        ((PETAL_COLOR >> 8) & 0xff) / 255.0,
                        (PETAL_COLOR & 0xff) / 255.0);
            }
        }
    }

}
