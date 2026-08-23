package ruby.bamboo;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooMenus;
import ruby.bamboo.core.init.BambooParticles;

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
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    /** MenuType 用 DeferredRegister (石臼GUI等) */
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister
            .create(ForgeRegistries.MENU_TYPES, MODID);
    /** ParticleType 用 DeferredRegister (花びらパーティクル等) */
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(ForgeRegistries.PARTICLE_TYPES, MODID);
    /** EntityType 用 DeferredRegister (Chair 等) */
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.ENTITY_TYPES, MODID);

    /** 旧 EnumCreateTab.TAB_BAMBOO の後継。アイコンはたけのこ(仮)。 */
    public static final RegistryObject<CreativeModeTab> BAMBOO_TAB = CREATIVE_TABS.register("bamboo",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bamboomod"))
                    .icon(() -> BambooItems.STRAW.get().getDefaultInstance())
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
        IEventBus modEventBus = context.getModEventBus();

        // 登録順: BLOCKS/ITEMS を先に接続してから各初期化クラスで register 呼び出し
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        // コンテンツ登録 (DeferredRegisterへの登録は静的初期化時に実行される)
        BambooBlocks.init();
        BambooItems.init();
        BambooBlockEntities.init();
        BambooMenus.init();
        BambooParticles.init();
        BambooEntities.init();

        // レシピ登録 (FMLCommonSetupEvent で実行)
        ruby.bamboo.crafting.BambooRecipes.register(modEventBus);

        LOGGER.info("BambooMod {} initialized", MODID);
    }
}
