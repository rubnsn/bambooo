package ruby.bamboo.block;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * カエデ苗木 (maple_sapling)。
 * SakuraSaplingと同型、染料右クリックは無し。
 */
public class MapleSaplingBlock extends SaplingBlock {

    public static final AbstractTreeGrower MAPLE_TREE = new AbstractTreeGrower() {
        @Override
        protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(
                RandomSource rand, boolean hasFlowers) {
            return rand.nextInt(10) == 0 ? MapleTreeFeatures.MAPLE_BIG : MapleTreeFeatures.MAPLE;
        }
    };

    public MapleSaplingBlock(BlockBehaviour.Properties props) {
        super(MAPLE_TREE, props);
    }

    // コード生成用のTreeConfiguration（JSONと同形、葉はmaple_leave）
    // 通常: straight 4+2 / blob r2、大木: fancy分岐 (vanilla fancy_oak相当)
    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        if (big) {
            return new TreeConfiguration.TreeConfigurationBuilder(
                    BlockStateProvider.simple(BambooBlocks.MAPLE_LOG.get()),
                    new FancyTrunkPlacer(3, 11, 0),
                    BlockStateProvider.simple(leafState),
                    new FancyFoliagePlacer(
                            ConstantInt.of(2),
                            ConstantInt.of(4), 4),
                    new TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4))).ignoreVines().build();
        }
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.MAPLE_LOG.get()),
                new StraightTrunkPlacer(big ? 6 : 4, 2, 0),
                BlockStateProvider.simple(leafState),
                new BlobFoliagePlacer(
                        ConstantInt.of(big ? 3 : 2),
                        ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
    }
}
