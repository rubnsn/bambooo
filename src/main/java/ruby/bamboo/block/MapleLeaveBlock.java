package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * カエデの葉 (SakuraBlocks MAPLE_LEAVE 相当)。
 * SakuraLeaveのREDバリアント (0xC80010, petal_2, MASS 1.2F) を独立ブロック化。
 * テクスチャは broadleaf.png を BlockColor で乗算 (0xC80010)。
 * 花びら処理は {@link PetalEmitter} に委譲。
 */
public class MapleLeaveBlock extends LeavesBlock implements PetalEmitter {

    /** カエデの花びら/葉色 (旧 EnumLeave.RED) */
    public static final int PETAL_COLOR = 0xC80010;

    public MapleLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public int petalColor(BlockState state) {
        return PETAL_COLOR;
    }

    @Override
    public SimpleParticleType petalType(BlockState state) {
        return PetalEmitter.typeOf(2);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        // 通常 1/100、突風時は PetalWind で密になる + たまに1粒おかわり
        emitPetals(state, level, pos, rand);
    }

}
