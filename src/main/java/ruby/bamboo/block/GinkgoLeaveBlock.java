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

    /**
     * 距離連動の動的カリング (負荷軽減と密度調整を兼ねる)。
     * 原木隣接は現状通り。同種隣接かつ自距離が奇数 (1/3/5/7) の面だけ省略する。
     * 空気との隣接や奇数側の面が残るため穴は開かない。
     */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState,
            net.minecraft.core.Direction dir) {
        if (adjacentState.is(this)) {
            try {
                return (state.getValue(DISTANCE)&1) == 1;
            } catch (Exception e) {
                return false;
            }
        }
        return super.skipRendering(state, adjacentState, dir);
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
