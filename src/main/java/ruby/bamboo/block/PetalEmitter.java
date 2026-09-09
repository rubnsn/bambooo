package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.client.particle.PetalWind;
import ruby.bamboo.core.init.BambooParticles;

/**
 * 花びらParticleを舞わせる葉の共通処理 (旧 SakuraLeave#randomDisplayTick 相当)。
 * <p>
 * 4樹種の葉 (Sakura/Maple/Ginkgo/Hinoki) の animateTick 重複を集約。
 * 発生条件は共通: 1/chance (突風時は PetalWind で密に) かつ直下が空気。
 * 桜のみ COLOR プロパティで色・種別を返す。
 */
public interface PetalEmitter {

    /** 花びら色 (RGB int)。桜は COLOR プロパティ由来 */
    int petalColor(BlockState state);

    /** 花びら種別。桜は COLOR の petal 対応 (1→PETAL_1, 2→PETAL_2, 3→PETAL_3) */
    SimpleParticleType petalType(BlockState state);

    /** 発生頻度 (1/chance)。既定100、ヒノキのみ200 */
    default int petalChance() {
        return 100;
    }

    /** animateTick 本体。各葉は super.animateTick() の後にこれを呼ぶ */
    default void emitPetals(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextInt(PetalWind.spawnChance(petalChance(), level)) != 0) {
            return;
        }
        if (!level.getBlockState(pos.below()).isAir()) {
            return;
        }
        int rgb = petalColor(state);
        double r = ((rgb >> 16) & 0xff) / 255.0;
        double g = ((rgb >> 8) & 0xff) / 255.0;
        double b = (rgb & 0xff) / 255.0;
        SimpleParticleType petal = petalType(state);
        level.addParticle(petal,
                pos.getX() + rand.nextDouble(), pos.getY(), pos.getZ() + rand.nextDouble(), r, g, b);
        if (PetalWind.spawnExtra(rand, level)) {
            level.addParticle(petal,
                    pos.getX() + rand.nextDouble(), pos.getY(), pos.getZ() + rand.nextDouble(), r, g, b);
        }
    }

    /** 固定単色葉用の種別解決 (旧 petal 番号→ParticleType) */
    static SimpleParticleType typeOf(int petal) {
        return switch (petal) {
            case 2 -> BambooParticles.PETAL_2.get();
            case 3 -> BambooParticles.PETAL_3.get();
            default -> BambooParticles.PETAL_1.get();
        };
    }
}
