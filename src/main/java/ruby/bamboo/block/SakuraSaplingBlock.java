package ruby.bamboo.block;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 桜の苗木。旧 SakuraSapling (1.10.2) の移植。
 * <p>
 * 1/10 の確率で大木(fancy分岐、旧GenSakuraBigTree相当)。自然・骨粉成長の樹木は
 * SakuraTreeFeatures の JSON (data/bamboomod/worldgen/configured_feature/sakura*.json) で定義。
 * <p>
 * 染料右クリックで即座に色付きの桜が育つ (旧 onBlockActivated 相当、幹は桜固定)。
 * 全16染料対応 (旧8色は旧hex、欠落8色は染料色)。花びらは全色桜 (PETAL_1)。
 * 染料は成功時のみ1個消費 (骨粉と同様、クリエ除く)。
 * 骨粉はバニラ成長に委ねる (PASS返却)。
 */
public class SakuraSaplingBlock extends SaplingBlock {

    public static final AbstractTreeGrower SAKURA_TREE = new AbstractTreeGrower() {
        @Override
        protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(
                RandomSource rand, boolean hasFlowers) {
            return rand.nextInt(10) == 0 ? SakuraTreeFeatures.SAKURA_BIG : SakuraTreeFeatures.SAKURA;
        }
    };

    public SakuraSaplingBlock(BlockBehaviour.Properties props) {
        super(SAKURA_TREE, props);
    }

    /**
     * コード生成用のTreeConfiguration (JSON sakura/sakura_big と同形、葉だけ色付き)。
     * 染料成長用。JSON特徴は葉色を動的に差し替えられないためコード生成する
     * (MapleSaplingBlock.buildTreeConfig と同型)。
     */
    public static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        if (big) {
            return new TreeConfiguration.TreeConfigurationBuilder(
                    BlockStateProvider.simple(BambooBlocks.SAKURA_LOG.get().defaultBlockState()),
                    new FancyTrunkPlacer(3, 11, 0),
                    BlockStateProvider.simple(leafState),
                    new FancyFoliagePlacer(
                            ConstantInt.of(2),
                            ConstantInt.of(4), 4),
                    new TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4))).ignoreVines().build();
        }
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.SAKURA_LOG.get().defaultBlockState()),
                new StraightTrunkPlacer(4, 2, 0),
                BlockStateProvider.simple(leafState),
                new BlobFoliagePlacer(
                        ConstantInt.of(2),
                        ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !(held.getItem() instanceof DyeItem dyeItem)) {
            return InteractionResult.PASS;
        }
        SakuraLeaveColor leafColor = SakuraLeaveColor.fromDye(dyeItem.getDyeColor());
        if (leafColor == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        ServerLevel server = (ServerLevel) level;
        boolean big = server.random.nextInt(10) == 0;
        BlockState leafState = BambooBlocks.SAKURA_LEAVES.get().defaultBlockState()
                .setValue(SakuraLeaveBlock.COLOR, leafColor);
        TreeConfiguration config = buildTreeConfig(leafState, big);
        // 旧 generate() 通り苗木を空気にしてから生成、失敗時は復元
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 4);
        var feature = new ConfiguredFeature<>(Feature.TREE, config);
        if (!feature.place(server, server.getChunkSource().getGenerator(), server.random, pos)) {
            level.setBlock(pos, state, 4);
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        return InteractionResult.sidedSuccess(false);
    }
}
