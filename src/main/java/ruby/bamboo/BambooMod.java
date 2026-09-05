package ruby.bamboo;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import ruby.bamboo.core.config.MiniatureConfig;
import ruby.bamboo.core.config.SpringConfig;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooMenus;
import ruby.bamboo.core.init.BambooParticles;
import ruby.bamboo.core.init.SpringFluids;

import java.util.function.Supplier;

/**
 * BambooMod 1.21.1 (NeoForge) 移植版 メインクラス。
 * <p>
 * 旧 1.10.2 版 (ruby.bamboo.core.BambooCore) の @SidedProxy / FMLPreInit 方式を
 * Forge 1.20.1 標準の DeferredRegister + イベントバス方式に置き換え、
 * 1.21.1 で NeoForge 方式 (コンストラクタ注入 + DeferredBlock/DeferredItem/DeferredHolder) へ移行した。
 */
@Mod(BambooMod.MODID)
public class BambooMod {

    public static final String MODID = "bamboomod";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** ブロック用 DeferredRegister (全ブロックはここに集約される) */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    /** アイテム用 DeferredRegister (全アイテムはここに集約される) */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** クリエイティブタブ */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);
    /** BlockEntityType 用 DeferredRegister (JPChest 等) */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, MODID);
    /** MenuType 用 DeferredRegister (石臼GUI等) */
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(Registries.MENU, MODID);
    /** ParticleType 用 DeferredRegister (花びらパーティクル等) */
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(Registries.PARTICLE_TYPE, MODID);
    /** EntityType 用 DeferredRegister (Chair 等) */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(Registries.ENTITY_TYPE, MODID);
    /** RecipeSerializer 用 DeferredRegister (囲炉裏レシピ) */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(Registries.RECIPE_SERIALIZER, MODID);
    /** RecipeType 用 DeferredRegister (囲炉裏レシピ) */
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister
            .create(Registries.RECIPE_TYPE, MODID);
    /** FluidType 用 DeferredRegister (温泉) */
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister
            .create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);
    /** Fluid 用 DeferredRegister (温泉) */
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister
            .create(Registries.FLUID, MODID);

    /** 囲炉裏レシピの Serializer */
    public static final Supplier<RecipeSerializer<ruby.bamboo.crafting.cooking.BambooCampfireRecipe>> CAMPFIRE_SERIALIZER = RECIPE_SERIALIZERS
            .register("campfire", () -> new ruby.bamboo.crafting.cooking.BambooCampfireRecipe.Serializer());
    /** 囲炉裏レシピの Type */
    public static final Supplier<RecipeType<ruby.bamboo.crafting.cooking.BambooCampfireRecipe>> CAMPFIRE_RECIPE_TYPE = RECIPE_TYPES
            .register("campfire", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(MODID, "campfire")));
    /** 石臼レシピの Serializer */
    public static final Supplier<RecipeSerializer<ruby.bamboo.crafting.grind.BambooGrindRecipe>> MILLSTONE_SERIALIZER = RECIPE_SERIALIZERS
            .register("millstone", () -> new ruby.bamboo.crafting.grind.BambooGrindRecipe.Serializer());
    /** 石臼レシピの Type */
    public static final Supplier<RecipeType<ruby.bamboo.crafting.grind.BambooGrindRecipe>> MILLSTONE_RECIPE_TYPE = RECIPE_TYPES
            .register("millstone", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(MODID, "millstone")));

    /** カットブロックレシピの Serializer (プランA: B+K 動的レシピ) */
    public static final Supplier<RecipeSerializer<ruby.bamboo.crafting.CutBlockRecipe>> CUT_BLOCK_SERIALIZER = RECIPE_SERIALIZERS
            .register("cut_block", () -> new ruby.bamboo.crafting.CutBlockRecipe.Serializer());
    /** 温泉 FluidType */
    public static final Supplier<FluidType> HOT_SPRING_TYPE = FLUID_TYPES
            .register("bamboo_hot_spring", SpringFluids::createHotSpringType);
    /** 温泉 Fluid Source */
    public static final Supplier<BaseFlowingFluid> SPRING_WATER_SOURCE = FLUIDS
            .register("spring_water", () -> new BaseFlowingFluid.Source(SpringFluids.props()));
    /** 温泉 Fluid Flowing */
    public static final Supplier<BaseFlowingFluid> SPRING_WATER_FLOWING = FLUIDS
            .register("spring_water_flowing", () -> new BaseFlowingFluid.Flowing(SpringFluids.props()));

    /** 旧 EnumCreateTab.TAB_BAMBOO の後継。アイコンはたけのこ(仮)。 */
    public static final Supplier<CreativeModeTab> BAMBOO_TAB = CREATIVE_TABS.register("bamboo",
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

    public BambooMod(IEventBus modEventBus, ModContainer modContainer) {
        // Config登録 (miniature particle 独自ルール)
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.CLIENT,
                MiniatureConfig.CLIENT_SPEC, "bamboomod-miniature.toml");
        // Config GUI — ModList の「Config」ボタンを有効化 (DistExecutorは1.21で削除のため、dist分岐+クライアント専用クラスで分離)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ruby.bamboo.client.ClientConfigRegistration.register(modContainer);
        }
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON,
                SpringConfig.COMMON_SPEC, "bamboomod-spring.toml");
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON,
                ruby.bamboo.core.config.WishConfig.COMMON_SPEC, "bamboomod-wish.toml");

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
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        ruby.bamboo.core.init.BambooCapabilities.init(modEventBus);

        // コンテンツ登録 (DeferredRegisterへの登録は静的初期化時に実行される)
        BambooBlocks.init();
        BambooItems.init();
        BambooBlockEntities.init();
        BambooMenus.init();
        BambooParticles.init();
        BambooEntities.init();
        ruby.bamboo.core.init.BambooEnchantments.init();

        // レシピ登録 (FMLCommonSetupEvent で実行)
        ruby.bamboo.crafting.BambooRecipes.register(modEventBus);

        // ネットワーク登録 (RegisterPayloadHandlersEvent を mod バスへ)
        ruby.bamboo.network.BambooNetwork.register(modEventBus);

        LOGGER.info("BambooMod {} initialized", MODID);
    }
}
