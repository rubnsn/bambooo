package ruby.bamboo.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.phys.BlockHitResult;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 桜の苗木。旧 SakuraSapling (1.10.2) の移植。
 * <p>
 * 1/10 の確率で大木 (旧 generateTree)。
 * 樹木の実体は SakuraTreeFeatures のキーが指す動的レジストリ JSON
 * (data/bamboomod/worldgen/configured_feature/sakura*.json) で定義する。
 * <p>
 * <b>染料右クリック (旧 onBlockActivated)</b>:
 * 手持ちが対応する染料なら、その色の葉を持つ桜を即座に生成する。
 * 広葉属(GREEN/RED/YELLOW/ORANGE)は broad_leave_* ブロック、
 * 桜属(WHITE/PURPLE/MAGENTA/PINK)は sakura_leave ブロックを葉として使う。
 */
public class SakuraSaplingBlock extends SaplingBlock {

    public static final AbstractTreeGrower SAKURA_TREE = new AbstractTreeGrower() {
        @Override
        protected net.minecraft.resources.ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(
                RandomSource rand, boolean hasFlowers) {
            return rand.nextInt(10) == 0 ? SakuraTreeFeatures.SAKURA_BIG : SakuraTreeFeatures.SAKURA;
        }
    };

    /** 染料アイテム → 葉ブロックサプライヤ (旧 EnumLeave.getLeaveFromDye 相当) */
    private static final Map<Item, java.util.function.Supplier<Block>> DYE_TO_LEAVES = Map.ofEntries(
            Map.entry(Items.BONE_MEAL, (java.util.function.Supplier<Block>) () -> BambooBlocks.SAKURA_LEAVES.get()), // WHITE
            Map.entry(Items.PURPLE_DYE, (java.util.function.Supplier<Block>) () -> BambooBlocks.SAKURA_LEAVES.get()), // PURPLE
            Map.entry(Items.MAGENTA_DYE, (java.util.function.Supplier<Block>) () -> BambooBlocks.SAKURA_LEAVES.get()), // MAGENTA
            Map.entry(Items.PINK_DYE, (java.util.function.Supplier<Block>) () -> BambooBlocks.SAKURA_LEAVES.get()), // PINK
            Map.entry(Items.GREEN_DYE,
                    () -> BambooBlocks.BROAD_LEAVES.get(BroadLeaveBlock.Variant.GREEN.ordinal()).get()),
            Map.entry(Items.RED_DYE,
                    () -> BambooBlocks.BROAD_LEAVES.get(BroadLeaveBlock.Variant.RED.ordinal()).get()),
            Map.entry(Items.YELLOW_DYE,
                    () -> BambooBlocks.BROAD_LEAVES.get(BroadLeaveBlock.Variant.YELLOW.ordinal()).get()),
            Map.entry(Items.ORANGE_DYE,
                    () -> BambooBlocks.BROAD_LEAVES.get(BroadLeaveBlock.Variant.ORANGE.ordinal()).get()));

    public SakuraSaplingBlock(BlockBehaviour.Properties props) {
        super(SAKURA_TREE, props);
    }

    // ===== 染料右クリック → 色付き木生成 (旧 onBlockActivated 相当) =====

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        var leafSupplier = DYE_TO_LEAVES.get(held.getItem());
        if (leafSupplier != null && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            RandomSource rand = level.getRandom();
            BlockState leafState = leafSupplier.get().defaultBlockState();
            boolean big = rand.nextInt(10) == 0;

            // サップリングを一時除去して生成 (旧 generate 相当)
            level.removeBlock(pos, false);
            boolean ok = new ConfiguredFeature<>(Feature.TREE, buildTreeConfig(leafState, big))
                    .place(serverLevel, serverLevel.getChunkSource().getGenerator(), rand, pos);
            if (!ok) {
                // 失敗時は元に戻す
                level.setBlock(pos, state, 3);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.CONSUME;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    /**
     * コード生成用の TreeConfiguration。
     * 既存 JSON 特徴 (sakura / sakura_big) と同じ形状で葉だけ差し替える。
     */
    private static TreeConfiguration buildTreeConfig(BlockState leafState, boolean big) {
        TreeConfiguration.TreeConfigurationBuilder builder = new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(BambooBlocks.SAKURA_LOG.get()),
                new StraightTrunkPlacer(big ? 6 : 4, 2, 0),
                BlockStateProvider.simple(leafState),
                new BlobFoliagePlacer(
                        net.minecraft.util.valueproviders.ConstantInt.of(big ? 3 : 2),
                        net.minecraft.util.valueproviders.ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1));
        builder.ignoreVines();
        return builder.build();
    }
}