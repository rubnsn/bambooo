package ruby.bamboo;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import ruby.bamboo.client.ClientConfigRegistration;
import ruby.bamboo.core.config.ColoredLightConfig;
import ruby.bamboo.core.config.MiniatureConfig;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.config.WishConfig;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooEnchantments;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooMenus;
import ruby.bamboo.core.init.BambooParticles;
import ruby.bamboo.core.init.SpringFluids;
import ruby.bamboo.crafting.BambooRecipes;
import ruby.bamboo.crafting.CutBlockRecipe;
import ruby.bamboo.crafting.cooking.BambooCampfireRecipe;
import ruby.bamboo.crafting.grind.BambooGrindRecipe;
import ruby.bamboo.network.BambooNetwork;

/**
 * BambooMod 1.20.1 移植版 メインクラス。
 * <p>
 * 旧 1.10.2 版 (ruby.bamboo.core.BambooCore) の @SidedProxy / FMLPreInit 方式を
 * Forge 1.20.1 標準の DeferredRegister + イベントバス方式に置き換えた。
 */
@Mod(BambooMod.MODID)
public class BambooMod {

    public static final String MODID = "bamboomod";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** ブロック用 DeferredRegister (全ブロックはここに集約される) */
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    /** アイテム用 DeferredRegister (全アイテムはここに集約される) */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    /** クリエイティブタブ */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);
    /** BlockEntityType 用 DeferredRegister (JPChest 等) */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    /** MenuType 用 DeferredRegister (石臼GUI等) */
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(ForgeRegistries.MENU_TYPES, MODID);
    /** ParticleType 用 DeferredRegister (花びらパーティクル等) */
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(ForgeRegistries.PARTICLE_TYPES, MODID);
    /** EntityType 用 DeferredRegister (Chair 等) */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.ENTITY_TYPES, MODID);
    /** RecipeSerializer 用 DeferredRegister (囲炉裏レシピ) */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    /** RecipeType 用 DeferredRegister (囲炉裏レシピ) */
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(ForgeRegistries.RECIPE_TYPES, MODID);
    /** Enchantment 用 DeferredRegister (腕輪エンチャント13種) */
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister
            .create(Registries.ENCHANTMENT, MODID);
    /** FluidType 用 DeferredRegister (温泉) */
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister
            .create(ForgeRegistries.Keys.FLUID_TYPES, MODID);
    /** Fluid 用 DeferredRegister (温泉) */
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister
            .create(ForgeRegistries.FLUIDS, MODID);

    /** 囲炉裏レシピの Serializer */
    public static final RegistryObject<RecipeSerializer<BambooCampfireRecipe>> CAMPFIRE_SERIALIZER = RECIPE_SERIALIZERS
            .register("campfire", () -> new BambooCampfireRecipe.Serializer());
    /** 囲炉裏レシピの Type */
    public static final RegistryObject<RecipeType<BambooCampfireRecipe>> CAMPFIRE_RECIPE_TYPE = RECIPE_TYPES
            .register("campfire", () -> RecipeType.simple(new ResourceLocation(MODID, "campfire")));
    /** 石臼レシピの Serializer */
    public static final RegistryObject<RecipeSerializer<BambooGrindRecipe>> MILLSTONE_SERIALIZER = RECIPE_SERIALIZERS
            .register("millstone", () -> new BambooGrindRecipe.Serializer());
    /** 石臼レシピの Type */
    public static final RegistryObject<RecipeType<BambooGrindRecipe>> MILLSTONE_RECIPE_TYPE = RECIPE_TYPES
            .register("millstone", () -> RecipeType.simple(new ResourceLocation(MODID, "millstone")));

    /** カットブロックレシピの Serializer (プランA: B+K 動的レシピ) */
    public static final RegistryObject<RecipeSerializer<CutBlockRecipe>> CUT_BLOCK_SERIALIZER = RECIPE_SERIALIZERS
            .register("cut_block", () -> new CutBlockRecipe.Serializer());
    /** 温泉 FluidType */
    public static final RegistryObject<FluidType> HOT_SPRING_TYPE = FLUID_TYPES
            .register("bamboo_hot_spring", SpringFluids::createHotSpringType);
    /** 温泉 Fluid Source */
    public static final RegistryObject<ForgeFlowingFluid> SPRING_WATER_SOURCE = FLUIDS
            .register("spring_water", () -> new ForgeFlowingFluid.Source(SpringFluids.props()));
    /** 温泉 Fluid Flowing */
    public static final RegistryObject<ForgeFlowingFluid> SPRING_WATER_FLOWING = FLUIDS
            .register("spring_water_flowing", () -> new ForgeFlowingFluid.Flowing(SpringFluids.props()));

    /** 旧 EnumCreateTab.TAB_BAMBOO の後継。アイコンはたけのこ(仮)。 */
    public static final RegistryObject<CreativeModeTab> BAMBOO_TAB = CREATIVE_TABS.register("bamboo",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bamboomod"))
                    .icon(() -> BambooItems.BAMBOO.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // 登録順に表示 (旧 ICreativeSoatName ソートの代替: 追加順 = 表示順)
                        // 注意: ForgeHooks は getCount()!=1 (空スタック含む) で例外を投げるため、
                        // BlockItem 未登録ブロック由来の AIR スタックは除外する
                        for (var s : BambooItems.CREATIVE_ITEMS) {
                            var st = s.get();
                            if (!st.isEmpty()) {
                                output.accept(st);
                            } else {
                                LOGGER.warn("Skipping empty stack in creative tab");
                            }
                        }
                    })
                    .build());

    public BambooMod(FMLJavaModLoadingContext context) {
        // Config登録 (miniature particle 独自ルール)
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                MiniatureConfig.CLIENT_SPEC, "bamboomod-miniature.toml");
        // 色付き光 (間接照明) 昼光抑制
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                ColoredLightConfig.CLIENT_SPEC, "bamboomod-coloredlight.toml");
        IEventBus modEventBus = context.getModEventBus();
        // Config GUI — ModList の「Config」ボタンを有効化（DEDICATED_SERVER では Screen をロードしないよう DistExecutor 経由で分離）
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> ClientConfigRegistration.register());
        }
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                SpringConfig.COMMON_SPEC, "bamboomod-spring.toml");
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                WishConfig.COMMON_SPEC, "bamboomod-wish.toml");

        // 登録順: BLOCKS/ITEMS を先に接続してから各初期化クラスで register 呼び出し
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);

        // コンテンツ登録 (DeferredRegisterへの登録は静的初期化時に実行される)
        BambooBlocks.init();
        BambooItems.init();
        BambooBlockEntities.init();
        BambooMenus.init();
        BambooParticles.init();
        BambooEntities.init();
        BambooEnchantments.init();

        // レシピ登録 (FMLCommonSetupEvent で実行)
        BambooRecipes.register(modEventBus);

        // ネットワーク登録
        BambooNetwork.register();

        LOGGER.info("BambooMod {} initialized", MODID);
    }
}
