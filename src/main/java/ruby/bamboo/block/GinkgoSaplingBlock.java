package ruby.bamboo.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
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

    public static final TreeGrower GINKGO_TREE = new TreeGrower("ginkgo", 0.1F,
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(GinkgoTreeFeatures.GINKGO), java.util.Optional.of(GinkgoTreeFeatures.GINKGO_BIG),
            java.util.Optional.empty(), java.util.Optional.empty());

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
