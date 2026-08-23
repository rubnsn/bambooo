package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooParticles;

/**
 * 広葉樹の葉 (旧 BroadLeave の移植)。
 * <p>
 * 旧版は1ブロック+VARIANT(meta 4-7)だったが、移植方針に従い4種の独立ブロック化。
 * テクスチャは共通 broadleaf.png を BlockColor ハンドラで色乗算する
 * (旧 colorMultiplier 相当、BambooClientSetup で登録)。
 * <p>
 * 花びらパーティクル: 旧 randomDisplayTick 相当 (1/100・直下空気・色=バリアント色)。
 */
public class BroadLeaveBlock extends LeavesBlock {

    /** 広葉のバリエーション (旧 EnumLeave の BROAD_LEAVES 属) */
    public enum Variant {
        GREEN("green", 0x3F9E55, 2),
        RED("red", 0xc80010, 2),
        YELLOW("yellow", 0xf5e600, 3),
        ORANGE("orange", 0xFFC600, 3);

        public final String name;
        /** 乗算用カラー (旧 EnumLeave#getColor 相当) */
        public final int color;
        /** 花びらテクスチャ番号 (旧 EnumLeave#getPetal 相当) */
        public final int petal;

        Variant(String name, int color, int petal) {
            this.name = name;
            this.color = color;
            this.petal = petal;
        }
    }

    public final Variant variant;

    public BroadLeaveBlock(Variant variant, Properties props) {
        super(props);
        this.variant = variant;
    }

    /**
     * 花びらパーティクル (旧 SakuraPetal エンティティの ParticleType 化)。
     * 1/100 の確率で直下が空気なら発生。色は速度引数経由で PetalParticle へ渡す。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        super.animateTick(state, level, pos, rand);
        if (rand.nextInt(100) != 0) {
            return;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).isAir()) {
            double x = pos.getX() + rand.nextDouble();
            double y = pos.getY();
            double z = pos.getZ() + rand.nextDouble();
            // 花びらテクスチャはバリアントごとに選択 (旧 getPetal 相当)
            var petalType = switch (variant.petal) {
                case 2 -> BambooParticles.PETAL_2.get();
                case 3 -> BambooParticles.PETAL_3.get();
                default -> BambooParticles.PETAL_1.get();
            };
            // xd/yd/zd に RGB をエンコードして色を渡す
            level.addParticle(petalType,
                    x, y, z,
                    ((variant.color >> 16) & 0xff) / 255.0,
                    ((variant.color >> 8) & 0xff) / 255.0,
                    (variant.color & 0xff) / 255.0);
        }
    }
}