package ruby.bamboo.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * ヒノキ苗木 (hinoki_sapling)。
 * 形状はヒノキ (HinokiTreeFeature) に近い円錐: 高く、下部幹露出、三角錐葉。
 * PineFoliagePlacer で再現。
 */
public class HinokiSaplingBlock extends SaplingBlock {

    public static final TreeGrower HINOKI_TREE = new TreeGrower("hinoki", 0.1F,
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(HinokiTreeFeatures.HINOKI), java.util.Optional.of(HinokiTreeFeatures.HINOKI_BIG),
            java.util.Optional.empty(), java.util.Optional.empty());

    public HinokiSaplingBlock(BlockBehaviour.Properties props) {
        super(HINOKI_TREE, props);
    }

    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        // ヒノキ: 高め + 円錐
        // 直幹 6+2 (big 8+2相当) + Pine 葉 (radius 2-3, height 4)
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.HINOKI_LOG.get()),
                new StraightTrunkPlacer(big ? 8 : 6, 2, 1),
                BlockStateProvider.simple(leafState),
                new PineFoliagePlacer(
                        net.minecraft.util.valueproviders.ConstantInt.of(1),
                        net.minecraft.util.valueproviders.ConstantInt.of(1),
                        net.minecraft.util.valueproviders.ConstantInt.of(big ? 4 : 3)),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
    }
}
