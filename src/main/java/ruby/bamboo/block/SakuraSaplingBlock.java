package ruby.bamboo.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 桜の苗木。旧 SakuraSapling (1.10.2) の移植。
 * <p>
 * 1/10 の確率で大木。樹木は SakuraTreeFeatures の JSON
 * (data/bamboomod/worldgen/configured_feature/sakura*.json) で定義。
 * 染料による葉色変えはオミット — 桜は桜の幹+桜の葉の固定種で生成する。
 */
public class SakuraSaplingBlock extends SaplingBlock {

    public static final AbstractTreeGrower SAKURA_TREE = new AbstractTreeGrower() {
        @Override
        protected net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(
                RandomSource rand, boolean hasFlowers) {
            return rand.nextInt(10) == 0 ? SakuraTreeFeatures.SAKURA_BIG : SakuraTreeFeatures.SAKURA;
        }
    };

    public SakuraSaplingBlock(BlockBehaviour.Properties props) {
        super(SAKURA_TREE, props);
    }
}