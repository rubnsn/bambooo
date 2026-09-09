package ruby.bamboo.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * イチョウ苗木 (ginkgo_sapling)。
 */
public class GinkgoSaplingBlock extends SaplingBlock {

    public static final AbstractTreeGrower GINKGO_TREE = new AbstractTreeGrower() {
        @Override
        protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> getConfiguredFeature(
                RandomSource rand, boolean hasFlowers) {
            return rand.nextInt(10) == 0 ? GinkgoTreeFeatures.GINKGO_BIG : GinkgoTreeFeatures.GINKGO;
        }
    };

    public GinkgoSaplingBlock(BlockBehaviour.Properties props) {
        super(GINKGO_TREE, props);
    }

    // 通常: straight 4+2 / blob r2、大木: fancy分岐 (vanilla fancy_oak相当)
    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        if (big) {
            return new TreeConfiguration.TreeConfigurationBuilder(
                    BlockStateProvider.simple(BambooBlocks.GINKGO_LOG.get()),
                    new FancyTrunkPlacer(3, 11, 0),
                    BlockStateProvider.simple(leafState),
                    new FancyFoliagePlacer(
                            net.minecraft.util.valueproviders.ConstantInt.of(2),
                            net.minecraft.util.valueproviders.ConstantInt.of(4), 4),
                    new TwoLayersFeatureSize(0, 0, 0, java.util.OptionalInt.of(4))).ignoreVines().build();
        }
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.GINKGO_LOG.get()),
                new StraightTrunkPlacer(big ? 6 : 4, 2, 0),
                BlockStateProvider.simple(leafState),
                new BlobFoliagePlacer(
                        net.minecraft.util.valueproviders.ConstantInt.of(big ? 3 : 2),
                        net.minecraft.util.valueproviders.ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
    }
}
