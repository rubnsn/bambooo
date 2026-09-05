package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * カエデ苗木 (maple_sapling)。
 * SakuraSaplingと同型、染料右クリックは無し。
 */
public class MapleSaplingBlock extends SaplingBlock {

    public static final TreeGrower MAPLE_TREE = new TreeGrower("maple", 0.1F,
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(MapleTreeFeatures.MAPLE), java.util.Optional.of(MapleTreeFeatures.MAPLE_BIG),
            java.util.Optional.empty(), java.util.Optional.empty());

    public MapleSaplingBlock(BlockBehaviour.Properties props) {
        super(MAPLE_TREE, props);
    }

    // コード生成用のTreeConfiguration（JSONと同形、葉はmaple_leave）
    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.MAPLE_LOG.get()),
                new StraightTrunkPlacer(big ? 6 : 4, 2, 0),
                BlockStateProvider.simple(leafState),
                new BlobFoliagePlacer(
                        net.minecraft.util.valueproviders.ConstantInt.of(big ? 3 : 2),
                        net.minecraft.util.valueproviders.ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
    }
}
