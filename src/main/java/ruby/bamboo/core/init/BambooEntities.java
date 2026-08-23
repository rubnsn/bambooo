package ruby.bamboo.core.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.ChairEntity;

/**
 * EntityType登録。旧 Chair エンティティの1.20.1移植。
 */
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

    public static void init() {
    }
}
