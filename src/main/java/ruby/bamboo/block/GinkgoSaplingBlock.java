package ruby.bamboo.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
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

    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
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
