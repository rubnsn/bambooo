package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ヒノキの葉 (SakuraBlocks HINOKI_LEAVE 相当、GREEN)。
 * 旧 HINOKI_LEAVE は花びら無しだったが、ゲーム的演出として低頻度(1/200)で緑の葉が舞う。
 * 色 0x3F9E55 (旧 EnumLeave.GREEN), petal_1, MASS 1.0F。
 * 花びら処理は {@link PetalEmitter} に委譲。
 */
public class HinokiLeaveBlock extends LeavesBlock implements PetalEmitter {

    public static final int PETAL_COLOR = 0x3F9E55;

    public HinokiLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public int petalColor(BlockState state) {
        return PETAL_COLOR;
    }

    @Override
    public SimpleParticleType petalType(BlockState state) {
        return PetalEmitter.typeOf(1);
    }

    @Override
    public int petalChance() {
        return 200;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        // ヒノキは低頻度 (通常 1/200)。突風時は PetalWind で密になる + たまに1粒おかわり
        emitPetals(state, level, pos, rand);
    }

}
