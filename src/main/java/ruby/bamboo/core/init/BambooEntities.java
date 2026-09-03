package ruby.bamboo.core.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ChairEntity;
import ruby.bamboo.entity.KaginawaHookEntity;
import ruby.bamboo.entity.arrow.BambooArrowEntity;
import ruby.bamboo.entity.arrow.ExplodeArrowEntity;
import ruby.bamboo.entity.arrow.LightArrowEntity;
import ruby.bamboo.entity.arrow.TorchArrowEntity;
import ruby.bamboo.entity.companion.DolphinCompanionEntity;
import ruby.bamboo.entity.companion.LlamaCompanionEntity;

/**
 * EntityType登録。旧 Chair エンティティの1.20.1移植。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BambooEntities {

    /**
     * 布団用座りエンティティ (旧 Chair)。
     * サイズ 0.3x0.3 (ユーザ指定)、noSummon/noSave同等は save空で実現。
     * 当たり判定ほぼ無し・非衝突。
     */
    public static final RegistryObject<EntityType<ChairEntity>> HUTON_CHAIR = BambooMod.ENTITY_TYPES.register(
            "huton_chair",
            () -> EntityType.Builder.<ChairEntity>of(ChairEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("huton_chair"));

    /**
     * 鈎縄フック (刀右クリック用)。0.5x0.5、追跡10。
     * 単一フック仕様。ブロックに固着しロープでプレイヤーを拘束。
     */
    public static final RegistryObject<EntityType<KaginawaHookEntity>> KAGINAWA_HOOK = BambooMod.ENTITY_TYPES.register(
            "kaginawa_hook",
            () -> EntityType.Builder.<KaginawaHookEntity>of(KaginawaHookEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("kaginawa_hook"));

    /**
     * 風 (旧 Wind。扇子用)。5x5の巨大判定、寿命5tick、葉破壊。
     * 当たり判定は巨大だがエンティティ衝突は無効、ブロック走査のみ。
     */
    public static final RegistryObject<EntityType<ruby.bamboo.entity.WindEntity>> WIND = BambooMod.ENTITY_TYPES.register(
            "wind",
            () -> EntityType.Builder.<ruby.bamboo.entity.WindEntity>of(ruby.bamboo.entity.WindEntity::new, MobCategory.MISC)
                    .sized(5.0F, 5.0F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("wind"));

    // ===== 弓矢エンティティ (旧 entity/arrow の 1.20.1 移植) =====

    /** 竹矢 (旧 EntityBambooArrow)。連射あり */
    public static final RegistryObject<EntityType<BambooArrowEntity>> BAMBOO_ARROW = registerArrow(
            "bamboo_arrow", BambooArrowEntity::new);

    /** 松明矢 (旧 EntityTorchArrow)。着地で松明設置 */
    public static final RegistryObject<EntityType<TorchArrowEntity>> TORCH_ARROW = registerArrow(
            "torch_arrow", TorchArrowEntity::new);

    /** 軽量矢 (旧 EntityLightArrow)。重力半減 */
    public static final RegistryObject<EntityType<LightArrowEntity>> LIGHT_ARROW = registerArrow(
            "light_arrow", LightArrowEntity::new);

    /** 爆発矢 (旧 EntityExplodeArrow)。時限爆発 */
    public static final RegistryObject<EntityType<ExplodeArrowEntity>> EXPLODE_ARROW = registerArrow(
            "explode_arrow", ExplodeArrowEntity::new);

    /** 手裏剣 (stone/iron/diamond共通、見た目はItemStackで切替) */
    public static final RegistryObject<EntityType<ruby.bamboo.entity.ShurikenEntity>> SHURIKEN = BambooMod.ENTITY_TYPES.register(
            "shuriken",
            () -> EntityType.Builder.<ruby.bamboo.entity.ShurikenEntity>of(ruby.bamboo.entity.ShurikenEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("shuriken"));

    /**
     * かんしゃく玉 (旧 EntityFirecracker)。5種共通、見た目・挙動はItemStack/Typeで切替。
     * 0.25x0.25、バウンド物理のため更新頻度は高め。
     */
    public static final RegistryObject<EntityType<ruby.bamboo.entity.FirecrackerEntity>> FIRECRACKER = BambooMod.ENTITY_TYPES.register(
            "firecracker",
            () -> EntityType.Builder.<ruby.bamboo.entity.FirecrackerEntity>of(ruby.bamboo.entity.FirecrackerEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("firecracker"));

    /** イルカ仲間 (RideableDolphin) - 蔵付き操作可能、水上ホーム制限、染料染色 */
    public static final RegistryObject<EntityType<DolphinCompanionEntity>> DOLPHIN_COMPANION = BambooMod.ENTITY_TYPES.register(
            "dolphin_companion",
            () -> EntityType.Builder.of(DolphinCompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("dolphin_companion"));

    /** ラマ仲間 (RideableLlama) - 蔵付き操作可能 */
    public static final RegistryObject<EntityType<LlamaCompanionEntity>> LLAMA_COMPANION = BambooMod.ENTITY_TYPES.register(
            "llama_companion",
            () -> EntityType.Builder.of(LlamaCompanionEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.87F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("llama_companion"));

    /** 釣りウキ (bamboo_rod 専用。ロープで繋がる) */
    public static final RegistryObject<EntityType<ruby.bamboo.entity.FishingBobberEntity>> FISHING_BOBBER = BambooMod.ENTITY_TYPES.register(
            "fishing_bobber",
            () -> EntityType.Builder.<ruby.bamboo.entity.FishingBobberEntity>of(ruby.bamboo.entity.FishingBobberEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(2)
                    .build("fishing_bobber"));

    private static <T extends AbstractArrow> RegistryObject<EntityType<T>> registerArrow(
            String name, EntityType.EntityFactory<T> factory) {
        return BambooMod.ENTITY_TYPES.register(name,
                () -> EntityType.Builder.<T>of(factory, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(4)
                        .updateInterval(20)
                        .build(name));
    }

    public static void init() {
    }

    @SubscribeEvent
    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(DOLPHIN_COMPANION.get(), DolphinCompanionEntity.createAttributes().build());
        event.put(LLAMA_COMPANION.get(), LlamaCompanionEntity.createAttributes().build());
    }
}
