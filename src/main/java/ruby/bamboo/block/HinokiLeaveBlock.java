package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooParticles;

/**
 * ヒノキの葉 (SakuraBlocks HINOKI_LEAVE 相当、GREEN)。
 * 旧 HINOKI_LEAVE は花びら無しだったが、ゲーム的演出として低頻度(1/200)で緑の葉が舞う。
 * 色 0x3F9E55 (旧 EnumLeave.GREEN), petal_2, MASS 1.0F。
 */
public class HinokiLeaveBlock extends LeavesBlock {

    public static final int PETAL_COLOR = 0x3F9E55;

    public HinokiLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        // ヒノキは低頻度 (1/200) で緑葉を散らす。旧Green(0x3F9E55, petal_1)と同色・同テクスチャ
        if (rand.nextInt(200) != 0) {
            return;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            double x = pos.getX() + rand.nextDouble();
            double y = pos.getY();
            double z = pos.getZ() + rand.nextDouble();
            level.addParticle(BambooParticles.PETAL_1.get(),
                    x, y, z,
                    ((PETAL_COLOR >> 16) & 0xff) / 255.0,
                    ((PETAL_COLOR >> 8) & 0xff) / 255.0,
                    (PETAL_COLOR & 0xff) / 255.0);
        }
    }

}
