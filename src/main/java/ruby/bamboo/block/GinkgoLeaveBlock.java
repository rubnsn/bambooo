package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * イチョウの葉 (SakuraBlocks GINKGO_LEAVE 相当)。
 * 黄色 0xF5E600, petal_3, MASS 1.2F。
 * 花びら処理は {@link PetalEmitter} に委譲。
 */
public class GinkgoLeaveBlock extends LeavesBlock implements PetalEmitter {

    public static final int PETAL_COLOR = 0xF5E600;

    public GinkgoLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public int petalColor(BlockState state) {
        return PETAL_COLOR;
    }

    @Override
    public SimpleParticleType petalType(BlockState state) {
        return PetalEmitter.typeOf(3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        // 通常 1/100、突風時は PetalWind で密になる + たまに1粒おかわり
        emitPetals(state, level, pos, rand);
    }

}
