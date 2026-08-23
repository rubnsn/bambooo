package ruby.bamboo.item.katana;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooEntities;

/**
 * 刀の特殊ドロップ登録表 (旧 KatanaDrops#regist の移植)。
 * <p>
 * ドロップ内容・確率はバニラ準拠の loot table JSON
 * (data/bamboomod/loot_tables/entities/katana/*.json) へ外出し。
 * 本クラスは「EntityType → 候補テーブル」の対応のみを登録する。
 * <p>
 * 旧仕様の維持点: 旧コードの記述どおり、Blaze 登録直後の
 * Glowstone Dust 0.2 は Ghast への追加として再現する (port-spec-katana.md §5)。
 */
public final class KatanaDrops {

    private static final String P = "entities/katana/";

    private KatanaDrops() {
    }

    /** FMLCommonSetupEvent 内で呼ぶ */
    public static void register() {
        // Zombie
        KatanaDropManager.addDrop(EntityType.ZOMBIE, "zombie_leather", "zombie_bone", "zombie_head");
        // Skeleton
        KatanaDropManager.addDrop(EntityType.SKELETON, "skeleton_skull", "wither_skeleton_skull");
        // Creeper
        KatanaDropManager.addDrop(EntityType.CREEPER, "creeper_gunpowder", "creeper_tnt", "creeper_head");
        // Spider
        KatanaDropManager.addDrop(EntityType.SPIDER, "spider_web");
        // Ghast
        KatanaDropManager.addDrop(EntityType.GHAST, "ghast_fire_charge", "ghast_tnt", "ghast_glowstone", "ghast_glowstone2");
        // Enderman
        KatanaDropManager.addDrop(EntityType.ENDERMAN, "enderman_poppy", "enderman_dandelion",
                "enderman_brown_mushroom", "enderman_red_mushroom", "enderman_ender_eye",
                "enderman_bamboo_shoot", "enderman_sakura_sapling");
        // Silverfish
        KatanaDropManager.addDrop(EntityType.SILVERFISH, "silverfish_paper");
        // Blaze
        KatanaDropManager.addDrop(EntityType.BLAZE, "blaze_fire_charge");
        // Pig
        KatanaDropManager.addDrop(EntityType.PIG, "pig_leather");
        // Sheep
        KatanaDropManager.addDrop(EntityType.SHEEP, "sheep_string");
        // Bat
        KatanaDropManager.addDrop(EntityType.BAT, "bat_apple", "bat_golden_apple");
        // Witch
        KatanaDropManager.addDrop(EntityType.WITCH, "witch_lily_pad", "witch_glass_bottle");
        // Chicken
        KatanaDropManager.addDrop(EntityType.CHICKEN, "chicken_egg");
        // Wolf
        KatanaDropManager.addDrop(EntityType.WOLF, "wolf_bone", "wolf_beef", "wolf_porkchop",
                "wolf_chicken", "wolf_rabbit", "wolf_feather", "wolf_mutton", "wolf_leather");
        // Cat (旧 Ocelot)
        KatanaDropManager.addDrop(EntityType.CAT, "cat_cod", "cat_chicken");
        KatanaDropManager.addDrop(EntityType.OCELOT, "cat_cod", "cat_chicken");

        BambooMod.LOGGER.info("Registered katana drop tables for {} entity types",
                KatanaDropManager.getTableCount());
    }
}
