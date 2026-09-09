package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 桜の葉。旧 SakuraLeave (1.10.2) の移植。
 * <p>
 * 旧版は常時光レベル12相当の発光 + 非透明扱いだったため、1.20.1では
 * lightLevel(9) を与える。旧 randomTick での苗木自動生成は無限増殖を招くためオミット
 * （苗木は破壊時の loot_table でのみ入手）。
 * <p>
 * 葉色は COLOR プロパティ8色 (旧 EnumLeave)。染料で苗木から色付きの木が生える。
 * テクスチャは旧来の無彩色ベース (block/sakura) に BlockColor で色乗算する。
 * 花びら処理は {@link PetalEmitter} に委譲。
 */
public class SakuraLeaveBlock extends LeavesBlock implements PetalEmitter {

    public static final EnumProperty<SakuraLeaveColor> COLOR = EnumProperty.create("color", SakuraLeaveColor.class);

    /** 既定ピンク (旧 EnumLeave.PINK 0xFFC5CC)。既存ワールド互換のため既定値 */
    public static final int PETAL_COLOR = SakuraLeaveColor.PINK.color;

    public SakuraLeaveBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(COLOR, SakuraLeaveColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    @Override
    public int petalColor(BlockState state) {
        try {
            return state.getValue(COLOR).color;
        } catch (Exception e) {
            return PETAL_COLOR;
        }
    }

    @Override
    public SimpleParticleType petalType(BlockState state) {
        int petal;
        try {
            petal = state.getValue(COLOR).petal;
        } catch (Exception e) {
            petal = 1;
        }
        return PetalEmitter.typeOf(petal);
    }

    /**
     * 花びらパーティクル (旧 SakuraLeave#randomDisplayTick 相当)。
     * 通常 1/100、突風時は PetalWind で密になる + たまに1粒おかわり。
     * 直下が空気のときのみ発生。色・種別は COLOR プロパティ由来。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        emitPetals(state, level, pos, rand);
    }
}
