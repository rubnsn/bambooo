package ruby.bamboo.core.init;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.AndonBlock;
import ruby.bamboo.block.BambooBlock;
import ruby.bamboo.block.BambooPaneBlock;
import ruby.bamboo.block.BambooPotBlock;
import ruby.bamboo.block.BambooShootBlock;
import ruby.bamboo.block.FlowerBedBlock;
import ruby.bamboo.block.IndLightBlock;
import ruby.bamboo.block.KitunebiBlock;
import ruby.bamboo.block.CampfireBlock;
import ruby.bamboo.block.GinkgoLeaveBlock;
import ruby.bamboo.block.GinkgoLogBlock;
import ruby.bamboo.block.GinkgoSaplingBlock;
import ruby.bamboo.block.HinokiLeaveBlock;
import ruby.bamboo.block.HinokiLogBlock;
import ruby.bamboo.block.HinokiSaplingBlock;
import ruby.bamboo.block.JPChestBlock;
import ruby.bamboo.block.LeafCarpetBlock;
import ruby.bamboo.block.MapleLeaveBlock;
import ruby.bamboo.block.MapleLogBlock;
import ruby.bamboo.block.MapleSaplingBlock;
import ruby.bamboo.block.MillStoneBlock;
import ruby.bamboo.block.MillBlock;
import ruby.bamboo.block.PaddyFieldBlock;
import ruby.bamboo.block.RicePlantBlock;
import ruby.bamboo.block.TomatoPlantBlock;
import ruby.bamboo.block.BeanPlantBlock;
import ruby.bamboo.block.SakuraLeaveBlock;
import ruby.bamboo.block.SakuraLogBlock;
import ruby.bamboo.block.SakuraSaplingBlock;
import ruby.bamboo.block.CutBlock;
import ruby.bamboo.block.HutonBlock;
import ruby.bamboo.block.MiniatureBlock;
import ruby.bamboo.block.BlindBlock;
import ruby.bamboo.block.NorenBlock;
import ruby.bamboo.block.SlideDoorBlock;
import ruby.bamboo.block.SpringBlock;
import ruby.bamboo.block.SpringWaterBlock;
import ruby.bamboo.block.TatamiBlock;
import ruby.bamboo.block.WallShelfBlock;
import ruby.bamboo.block.entity.MiniatureBlockEntity;
import ruby.bamboo.item.CampfireItem;
import ruby.bamboo.item.CutBlockItem;
import ruby.bamboo.item.MiniatureItem;

/**
 * ブロック登録。旧 BambooData.@BambooBlock アノテーション + DataLoader の置き換え。
 * <p>
 * 登録は {@link ruby.bamboo.BambooMod#BLOCKS} DeferredRegister に対して明示的に行う。
 * 名前は旧版の registry name と一致させている (既存ワールド/レシピ互換用)。
 */
public final class BambooBlocks {

    // ===== 縦スライス第1弾: シンプルブロック =====

    /** わらブロック (旧 wara / deco系)。slab・stairs共に無機能装飾 */
    public static final RegistryObject<Block> WARA = register("wara",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> WARA_SLAB = register("wara_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> WARA_STAIRS = register("wara_stairs",
            () -> new StairBlock(WARA.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));

    /** 桜の木材 (sakura_planks) */
    public static final RegistryObject<SakuraPlankBlock> SAKURA_PLANKS = register("sakura_planks",
            () -> new SakuraPlankBlock(props(MapColor.COLOR_PINK, SoundType.WOOD).strength(0.2f, 1.0f)));

    // ===== 竹柵/欄間系 (旧 bamboopane meta=0..3 を独立ブロック化) =====

    public static final RegistryObject<BambooPaneBlock> BAMBOO_PANE = registerPane(BambooPaneBlock.Variant.NORMAL);
    public static final RegistryObject<BambooPaneBlock> BAMBOO_PANE2 = registerPane(BambooPaneBlock.Variant.TAN1);
    public static final RegistryObject<BambooPaneBlock> BAMBOO_PANE3 = registerPane(BambooPaneBlock.Variant.TAN2);
    public static final RegistryObject<BambooPaneBlock> RANMA = registerPane(BambooPaneBlock.Variant.RANMA);

    // ===== 第2弾: デコレーション系 (kawara/plaster/namako/kaya/cbirch/coak/cpine + wara) =====

    public static final RegistryObject<Block> KAWARA = register("kawara",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> KAWARA_SLAB = register("kawara_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> KAWARA_STAIRS = register("kawara_stairs",
            () -> new StairBlock(KAWARA.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> PLASTER = register("plaster",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> PLASTER_SLAB = register("plaster_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> PLASTER_STAIRS = register("plaster_stairs",
            () -> new StairBlock(PLASTER.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> NAMAKO = register("namako",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> NAMAKO_SLAB = register("namako_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> NAMAKO_STAIRS = register("namako_stairs",
            () -> new StairBlock(NAMAKO.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> KAYA = register("kaya",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> KAYA_SLAB = register("kaya_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> KAYA_STAIRS = register("kaya_stairs",
            () -> new StairBlock(KAYA.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> CBIRCH = register("cbirch",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> CBIRCH_SLAB = register("cbirch_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> CBIRCH_STAIRS = register("cbirch_stairs",
            () -> new StairBlock(CBIRCH.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> COAK = register("coak",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> COAK_SLAB = register("coak_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> COAK_STAIRS = register("coak_stairs",
            () -> new StairBlock(COAK.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<Block> CPINE = register("cpine",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> CPINE_SLAB = register("cpine_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> CPINE_STAIRS = register("cpine_stairs",
            () -> new StairBlock(CPINE.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(0.5F, 300F)));

    // ===== 第3弾: 畫 (旧metaバリアントを4種の独立ブロック化) =====

    /** 畳 (通常) - 旧 Tatami meta=0 */
    public static final RegistryObject<TatamiBlock> TATAMI = register("tatami",
            () -> new TatamiBlock(TatamiBlock.Variant.NORMAL));
    /** 縁無し畳 - 旧 meta=1 */
    public static final RegistryObject<TatamiBlock> TATAMI_NON_BORDER = register("tatami_non_border",
            () -> new TatamiBlock(TatamiBlock.Variant.NON_BORDER));
    /** 日焼け畳 - 旧 meta=2 */
    public static final RegistryObject<TatamiBlock> TATAMI_TAN = register("tatami_tan",
            () -> new TatamiBlock(TatamiBlock.Variant.TAN));
    /** 縁無し日焼け畳 - 旧 meta=3 */
    public static final RegistryObject<TatamiBlock> TATAMI_TAN_NON_BORDER = register("tatami_tan_non_border",
            () -> new TatamiBlock(TatamiBlock.Variant.TAN_NON_BORDER));

    // ===== 第3弾: 間接照明16色 =====

    public static final List<RegistryObject<Block>> INDLIGHTS = registerIndLights();

    // ===== 第4弾: シンプル植物 (竹・たけのこ・稲・さくら系) =====

    /** 竹ブロック (旧 bamboo)。BlockItem無し (ドロップは別アイテム bamboo、植え付けは bamboo_shoot)。XZオフセット(seed揺らぎ)あり */
    public static final RegistryObject<BambooBlock> BAMBOO = registerNoItem("bamboo",
            () -> new BambooBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.BAMBOO)
                    .strength(0.5f).randomTicks().noOcclusion().dynamicShape()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    /** たけのこ (旧 bamboo_shoot / shoot) */
    public static final RegistryObject<BambooShootBlock> BAMBOO_SHOOT = register("bamboo_shoot",
            () -> new BambooShootBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.CROP)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** 稲 (旧 rice_plant) */
    public static final RegistryObject<RicePlantBlock> RICE_PLANT = registerNoItem("rice_plant",
            () -> new RicePlantBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.CROP)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** トマト (旧 tomatoPlant)。種兼用アイテム tomato で植える */
    public static final RegistryObject<TomatoPlantBlock> TOMATO_PLANT = registerNoItem("tomato_plant",
            () -> new TomatoPlantBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.CROP)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** 豆 (旧 beanPlant)。種兼用アイテム bean で植える */
    public static final RegistryObject<BeanPlantBlock> BEAN_PLANT = registerNoItem("bean_plant",
            () -> new BeanPlantBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.CROP)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** 田んぼ (旧 paddy_field / sakura PaddyField)。FarmlandBlock継承 + WATERLOGGED */
    public static final RegistryObject<PaddyFieldBlock> PADDY_FIELD = register("paddy_field",
            () -> new PaddyFieldBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT).sound(SoundType.GRAVEL)
                    .strength(0.6F).randomTicks()));

    /** 桜の苗木 (旧 sakura_sapling) */
    public static final RegistryObject<SakuraSaplingBlock> SAKURA_SAPLING = register("sakura_sapling",
            () -> new SakuraSaplingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** 桜の原木 (旧 sakura_log) */
    public static final RegistryObject<SakuraLogBlock> SAKURA_LOG = register("sakura_log",
            () -> new SakuraLogBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(2.0f)));

    /** 桜の葉 (旧 sakura_leave) - 旧 setLightLevel(0.75F) 相当で発光 */
    public static final RegistryObject<SakuraLeaveBlock> SAKURA_LEAVES = register("sakura_leave",
            () -> new SakuraLeaveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.2f).randomTicks().noOcclusion()
                    .lightLevel(state -> 9)
                    // 1.20.1では isSuffocating/isViewBlocking 等を noOcclusion に合わせて無効化
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    // ===== 新樹木: カエデ / イチョウ / ヒノキ (SakuraBlocks MAPLE/GINKGO/HINOKI相当) =====

    /** カエデ原木 (maple_log) */
    public static final RegistryObject<MapleLogBlock> MAPLE_LOG = register("maple_log",
            () -> new MapleLogBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(2.0f)));

    /** イチョウ原木 (ginkgo_log) */
    public static final RegistryObject<GinkgoLogBlock> GINKGO_LOG = register("ginkgo_log",
            () -> new GinkgoLogBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(2.0f)));

    /** ヒノキ原木 (hinoki_log) */
    public static final RegistryObject<HinokiLogBlock> HINOKI_LOG = register("hinoki_log",
            () -> new HinokiLogBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD)
                    .strength(2.0f)));

    /** カエデ葉 (maple_leave) - 赤 0xC80010 */
    public static final RegistryObject<MapleLeaveBlock> MAPLE_LEAVES = register("maple_leave",
            () -> new MapleLeaveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.2f).randomTicks().noOcclusion()
                    .lightLevel(state -> 9)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** イチョウ葉 (ginkgo_leave) - 黄 0xF5E600 */
    public static final RegistryObject<GinkgoLeaveBlock> GINKGO_LEAVES = register("ginkgo_leave",
            () -> new GinkgoLeaveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.2f).randomTicks().noOcclusion()
                    .lightLevel(state -> 9)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** ヒノキ葉 (hinoki_leave) - 緑 0x3F9E55 低頻度 */
    public static final RegistryObject<HinokiLeaveBlock> HINOKI_LEAVES = register("hinoki_leave",
            () -> new HinokiLeaveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.2f).randomTicks().noOcclusion()
                    .lightLevel(state -> 9)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** カエデ苗木 */
    public static final RegistryObject<MapleSaplingBlock> MAPLE_SAPLING = register("maple_sapling",
            () -> new MapleSaplingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** イチョウ苗木 */
    public static final RegistryObject<GinkgoSaplingBlock> GINKGO_SAPLING = register("ginkgo_sapling",
            () -> new GinkgoSaplingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** ヒノキ苗木 */
    public static final RegistryObject<HinokiSaplingBlock> HINOKI_SAPLING = register("hinoki_sapling",
            () -> new HinokiSaplingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT).sound(SoundType.GRASS)
                    .strength(0.0f).randomTicks().noCollission().instabreak()));

    /** 行灯 (旧 andon) - 旧 setLightLevel 相当で光レベル14 */
    public static final RegistryObject<AndonBlock> ANDON = register("andon",
            () -> new AndonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE).sound(SoundType.WOOD)
                    .strength(0.3f, 300f)
                    .lightLevel(state -> 14)
                    .noOcclusion()
                    // CUTOUT描画相当 (1.13+ではアイテム/ブロック形状から自動判定されるが明示)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** 狐火 (旧 kitunebi) - 不可視光源 (光レベル15)。手持ち時のみ可視 */
    public static final RegistryObject<KitunebiBlock> KITSUNEBI = register("kitunebi",
            () -> new KitunebiBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE).sound(SoundType.WOOL)
                    // 旧 setLightLevel(1F)=15 / setHardness(0) / MaterialNone(すり抜け+ピストン非対応)
                    .strength(0.0f).instabreak()
                    .lightLevel(state -> 15)
                    .noCollission().noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    /** 和風チェスト (旧 jpchest) - 54スロット BlockEntity、大チェスト連結なし */
    public static final RegistryObject<JPChestBlock> JP_CHEST = register("jpchest",
            JPChestBlock::new);

    /** 石臼 (旧 millstone) - 粉砕BlockEntity。インベントリアイコンはフラット(1枚絵) */
    public static final RegistryObject<MillStoneBlock> MILLSTONE = register("millstone",
            MillStoneBlock::new);

    /** 囲炉裏 (旧 campfire) - 調理BlockEntity。BER描画。BEWLR用に専用BlockItem */
    public static final RegistryObject<CampfireBlock> CAMPFIRE = registerCampfire();

    // ===== 第7弾: 引き戸 7種 (sakura-master準拠) =====
    public static final RegistryObject<SlideDoorBlock> SHOJI = registerSlideDoor("shoji", false);
    public static final RegistryObject<SlideDoorBlock> SHOJI_YOKOGUMI = registerSlideDoor("shoji_yokogumi", false);
    public static final RegistryObject<SlideDoorBlock> SHOJI_TATEGUMI = registerSlideDoor("shoji_tategumi", false);
    public static final RegistryObject<SlideDoorBlock> SHOJI_YUKIMI = registerSlideDoor("shoji_yukimi", true);
    public static final RegistryObject<SlideDoorBlock> HUSUMA = registerSlideDoor("husuma", false);
    public static final RegistryObject<SlideDoorBlock> GLASS_DOOR = registerSlideDoor("glassdoor", true);
    public static final RegistryObject<SlideDoorBlock> GLASS_DOOR_GRID = registerSlideDoor("glassdoor_grid", true);

    public static final List<RegistryObject<SlideDoorBlock>> SLIDE_DOORS = List.of(
            SHOJI, SHOJI_YOKOGUMI, SHOJI_TATEGUMI, SHOJI_YUKIMI, HUSUMA, GLASS_DOOR, GLASS_DOOR_GRID);

    // ===== 布団 (旧 huton) =====
    public static final RegistryObject<HutonBlock> HUTON = register("huton", HutonBlock::new);

    // ===== 壁棚 (sakura-master WallShelf) =====
    public static final RegistryObject<WallShelfBlock> WALL_SHELF = register("wall_shelf",
            WallShelfBlock::new);

    // ===== ミニチュア (箱庭) — 単一アイテム + NBT Size(4,8,12,16) =====
    public static final RegistryObject<MiniatureBlock> MINIATURE = registerMiniature();

    // ===== カットブロック — 単一ブロック + NBT CutState/YLevel/HLevel =====
    public static final RegistryObject<CutBlock> CUT_BLOCK = registerCutBlock();

    // ===== 竹鉢 (sakura BambooPot 移植) =====
    public static final RegistryObject<BambooPotBlock> BAMBOO_POT = register("bamboo_pot",
            BambooPotBlock::new);

    // ===== 花壇 (プランターの1ブロック拡大版。上面pot_top・側面/底面dirt、色・向きなし) =====
    // 入手はスコップ変換のみのためクリエタブには出さない (BlockItem自体は中クリック/asItem用に登録維持)
    public static final RegistryObject<FlowerBedBlock> FLOWER_BED = registerNoCreative("flower_bed",
            FlowerBedBlock::new);

    // ===== 源泉・温泉水 =====
    public static final RegistryObject<SpringBlock> SPRING_BLOCK = register("spring_block", SpringBlock::new);
    public static final RegistryObject<SpringWaterBlock> SPRING_WATER = registerNoItem("spring_water",
            () -> new SpringWaterBlock(() -> BambooMod.SPRING_WATER_SOURCE.get(),
                    BlockBehaviour.Properties.copy(Blocks.WATER).noLootTable().noOcclusion()));

    // ===== 風車・水車 (旧 EntityWindmill/EntityWaterwheel の Block+BE 移植。無機能装飾) =====

    /** 風車 (通常)。textures/entity/windmill.png */
    public static final RegistryObject<MillBlock> WINDMILL = register("windmill",
            () -> new MillBlock(MillBlock.Type.WINDMILL));

    /** 風車 (布張り)。textures/entity/windmill_cloth.png */
    public static final RegistryObject<MillBlock> WINDMILL_CLOTH = register("windmill_cloth",
            () -> new MillBlock(MillBlock.Type.WINDMILL_CLOTH));

    /** 水車。水に浸かると回転。textures/entity/waterwheel.png */
    public static final RegistryObject<MillBlock> WATERWHEEL = register("waterwheel",
            () -> new MillBlock(MillBlock.Type.WATERWHEEL));

    // ===== sakura無機能deco移植: 単独Block登録 21件 =====
    // sakura_slab (sakura 32): PlayerFacingSlab相当だが今回は SlabBlock で SakuraPlank 流用
    public static final RegistryObject<SlabBlock> SAKURA_SLAB = register("sakura_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PINK).sound(SoundType.WOOD).strength(2.0F)));

    // tatami_slab / tatami_tan_slab (sakura 81-83): TatamiBlock 流用 props + SlabBlock
    public static final RegistryObject<SlabBlock> TATAMI_SLAB = register("tatami_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).sound(SoundType.GRASS).strength(0.5F)));
    public static final RegistryObject<SlabBlock> TATAMI_TAN_SLAB = register("tatami_tan_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.GRASS).strength(0.5F)));

    // straw 3件 (sakura 93): straw_block / slab / stairs - Decorate系流用 (新規straw)
    public static final RegistryObject<Block> STRAW_BLOCK = register("straw_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW).sound(SoundType.GRASS).strength(0.5F, 300F)));
    public static final RegistryObject<SlabBlock> STRAW_SLAB = register("straw_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW).sound(SoundType.GRASS).strength(0.5F, 300F)));
    public static final RegistryObject<StairBlock> STRAW_STAIRS = register("straw_stairs",
            () -> new StairBlock(STRAW_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_YELLOW).sound(SoundType.GRASS).strength(0.5F, 300F)));

    // checkered 9件 (sakura 94-96): 市松 各cube+slab+stairs
    public static final RegistryObject<Block> CHECKERED_OAK = register("checkered_oak",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<SlabBlock> CHECKERED_OAK_SLAB = register("checkered_oak_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<StairBlock> CHECKERED_OAK_STAIRS = register("checkered_oak_stairs",
            () -> new StairBlock(CHECKERED_OAK.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F, 3.0F)));

    public static final RegistryObject<Block> CHECKERED_BIRCH = register("checkered_birch",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<SlabBlock> CHECKERED_BIRCH_SLAB = register("checkered_birch_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<StairBlock> CHECKERED_BIRCH_STAIRS = register("checkered_birch_stairs",
            () -> new StairBlock(CHECKERED_BIRCH.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.SAND).sound(SoundType.WOOD).strength(1.0F, 3.0F)));

    public static final RegistryObject<Block> CHECKERED_PINE = register("checkered_pine",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<SlabBlock> CHECKERED_PINE_SLAB = register("checkered_pine_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.WOOD).strength(1.0F, 3.0F)));
    public static final RegistryObject<StairBlock> CHECKERED_PINE_STAIRS = register("checkered_pine_stairs",
            () -> new StairBlock(CHECKERED_PINE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.WOOD).strength(1.0F, 3.0F)));

    // blind 1件 (sakura 46): PaneBlock/cutout/collision空
    public static final RegistryObject<BlindBlock> BLIND = register("blind", BlindBlock::new);

    // noren 2件 (sakura 47-48): PaneBlock/cutout/doesNotBlockMovement
    public static final RegistryObject<NorenBlock> NOREN_BLUE = register("noren_blue", NorenBlock::new);
    public static final RegistryObject<NorenBlock> NOREN_PURPLE = register("noren_purple", NorenBlock::new);

    // brick 3件 (sakura 51-53): cube + slab + stairs (v2: brick 3色にハーフ/階段追加)
    public static final RegistryObject<Block> BRICK_ORANGE = register("brick_orange",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<SlabBlock> BRICK_ORANGE_SLAB = register("brick_orange_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<StairBlock> BRICK_ORANGE_STAIRS = register("brick_orange_stairs",
            () -> new StairBlock(BRICK_ORANGE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_ORANGE).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> BRICK_WHITE = register("brick_white",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<SlabBlock> BRICK_WHITE_SLAB = register("brick_white_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<StairBlock> BRICK_WHITE_STAIRS = register("brick_white_stairs",
            () -> new StairBlock(BRICK_WHITE.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.QUARTZ).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> BRICK_BROWN = register("brick_brown",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<SlabBlock> BRICK_BROWN_SLAB = register("brick_brown_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<StairBlock> BRICK_BROWN_STAIRS = register("brick_brown_stairs",
            () -> new StairBlock(BRICK_BROWN.get().defaultBlockState(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BROWN).sound(SoundType.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    // ===== 葉カーペット 4種 (桜/ヒノキ/モミジ/イチョウ) — バニラカーペット同様 厚さ1/16 =====
    public static final RegistryObject<LeafCarpetBlock> SAKURA_CARPET = register("sakura_carpet",
            () -> new LeafCarpetBlock(MapColor.COLOR_PINK));
    public static final RegistryObject<LeafCarpetBlock> HINOKI_CARPET = register("hinoki_carpet",
            () -> new LeafCarpetBlock(MapColor.PLANT));
    public static final RegistryObject<LeafCarpetBlock> MAPLE_CARPET = register("maple_carpet",
            () -> new LeafCarpetBlock(MapColor.COLOR_RED));
    public static final RegistryObject<LeafCarpetBlock> GINKGO_CARPET = register("ginkgo_carpet",
            () -> new LeafCarpetBlock(MapColor.COLOR_YELLOW));

    private static List<RegistryObject<Block>> registerIndLights() {
        List<RegistryObject<Block>> result = new ArrayList<>();
        for (IndLightBlock.DyeColor color : IndLightBlock.DyeColor.values()) {
            String name = "indlight_" + color.name;
            RegistryObject<IndLightBlock> block = BambooMod.BLOCKS.register(name,
                    () -> new IndLightBlock(color));
            BambooMod.ITEMS.register(name,
                    () -> new BlockItem(block.get(), new Item.Properties()));
            BambooItems.addCreative(block);
            result.add((RegistryObject<Block>) (RegistryObject<?>) block);
        }
        return result;
    }

    /**
     * 静的初期化順序の保証用ダミー。BambooMod コンストラクタから呼ばれる。
     */
    public static void init() {
        // static フィールド初期化は本クラスがロードされた時点で完了している
    }

    private static BlockBehaviour.Properties props(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound);
    }

    /**
     * 竹柵登録ヘルパー: ブロック + BlockItem を同名で登録。
     * 登録名は Variant.regName (bamboo_pane / bamboo_pane2 / bamboo_pane3 / ranma)。
     */
    private static RegistryObject<BambooPaneBlock> registerPane(BambooPaneBlock.Variant variant) {
        RegistryObject<BambooPaneBlock> block = BambooMod.BLOCKS.register(variant.regName,
                () -> new BambooPaneBlock(variant));
        BambooMod.ITEMS.register(variant.regName,
                () -> new BlockItem(block.get(), new Item.Properties()));
        BambooItems.addCreative(block);
        return block;
    }

    /**
     * 汎用登録ヘルパー: ブロック + 対応するBlockItem を同名で登録。
     */
    private static <B extends Block> RegistryObject<B> register(String name,
            Supplier<? extends B> factory) {
        RegistryObject<B> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        BambooItems.addCreative(block);
        return block;
    }

    /**
     * BlockItem は登録するがクリエタブには出さない (入手経路が変換・生成のみのブロック用)。
     */
    private static <B extends Block> RegistryObject<B> registerNoCreative(String name,
            Supplier<? extends B> factory) {
        RegistryObject<B> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /** 囲炉裏の登録 (BEWLR用に専用BlockItemを使用) */
    private static RegistryObject<CampfireBlock> registerCampfire() {
        RegistryObject<CampfireBlock> block = BambooMod.BLOCKS.register("campfire", CampfireBlock::new);
        BambooMod.ITEMS.register("campfire",
                () -> new CampfireItem(block.get(), new Item.Properties()));
        BambooItems.addCreative(block);
        return block;
    }

    private static RegistryObject<SlideDoorBlock> registerSlideDoor(String name, boolean translucent) {
        RegistryObject<SlideDoorBlock> block = BambooMod.BLOCKS.register(name,
                () -> new SlideDoorBlock(SlideDoorBlock.createProp(translucent), translucent));
        BambooMod.ITEMS.register(name,
                () -> new DoubleHighBlockItem(block.get(), new Item.Properties()));
        BambooItems.addCreative(block);
        return block;
    }

    /** ミニチュア登録: 単一BlockItem + NBT違い4種をクリエタブへ */
    private static RegistryObject<MiniatureBlock> registerMiniature() {
        RegistryObject<MiniatureBlock> block = BambooMod.BLOCKS.register("miniature", MiniatureBlock::new);
        BambooMod.ITEMS.register("miniature", () -> new MiniatureItem(block.get(), new Item.Properties()));
        // クリエタブには Size 4,8,12,16 の4種を登録 (登録順=表示順)
        for (int s : new int[]{4, 8, 12, 16}) {
            final int size = s;
            BambooItems.addCreativeStack(() -> {
                ItemStack stack = new ItemStack(block.get());
                stack.getOrCreateTag().putInt(MiniatureBlockEntity.TAG_SIZE, size);
                // 空の場合は BlockEntityTag は不要
                return stack;
            });
        }
        return block;
    }

    /** カットブロック登録: 単一BlockItem(クリエタブ登録なし、ダミー透明) */
    private static RegistryObject<CutBlock> registerCutBlock() {
        RegistryObject<CutBlock> block = BambooMod.BLOCKS.register("cut_block", CutBlock::new);
        BambooMod.ITEMS.register("cut_block", () -> new CutBlockItem(block.get(), new Item.Properties()));
        // クリエタブ登録はしない（通常入手不能、レシピ生成のみ）
        return block;
    }

    /**
     * BlockItem 無しでブロックのみ登録 (作物等、種アイテムが別名で存在する場合用)。
     */
    private static <B extends Block> RegistryObject<B> registerNoItem(String name,
            Supplier<? extends B> factory) {
        return BambooMod.BLOCKS.register(name, factory);
    }
}
