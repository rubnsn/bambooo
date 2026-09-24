package ruby.bamboo.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;

/**
 * メイド服の強制装備。メイド服を持ってシフト+右クリックで村人の胴スロットへ入れる。
 * 村人側の右クリック (取引GUI) が先に消費するため EntityInteract で横取りする。
 * 胴ドロップ率は 0 に固定し死亡時ドロップを出さない。既存の胴装備は返却する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidClothesHandler {

    private MaidClothesHandler() {
    }

    @SubscribeEvent
    public static void onMaidEquip(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        ItemStack held = event.getItemStack();
        if (!held.is(BambooItems.MAID_CLOTHES.get())) return;

        ItemStack chest = villager.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty() && !chest.is(BambooItems.MAID_CLOTHES.get())) {
            ItemStack back = chest.copy();
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);
        villager.setItemSlot(EquipmentSlot.CHEST, toEquip);
        villager.setDropChance(EquipmentSlot.CHEST, 0.0F);
        villager.refreshDimensions();
        if (!player.isCreative()) {
            held.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Entity構築中 (armorItems未初期化) は Size が先に飛ぶため NPE を避けて未装備扱いにする。
     */
    private static boolean isMaidSafe(Villager villager) {
        try {
            if (!BambooItems.MAID_CLOTHES.isPresent()) return false;
            return villager.getItemBySlot(EquipmentSlot.CHEST).is(BambooItems.MAID_CLOTHES.get());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 小柄維持のためメイド服着用中は当たり判定を縮小する。
     * モデル高 24px (1.5) * 0.9375 = 1.406 に合わせ、高さ 1.5・目は顔中央 1.27。
     * SLEEPING はベッド判定のため触らない。子供は半分。
     */
    @SubscribeEvent
    public static void onMaidSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (event.getPose() == Pose.SLEEPING) return;
        if (!isMaidSafe(villager)) return;
        boolean baby = villager.isBaby();
        event.setNewSize(EntityDimensions.fixed(baby ? 0.3F : 0.6F, baby ? 0.75F : 1.5F));
        event.setNewEyeHeight(baby ? 0.635F : 1.27F);
    }

    /** 直接の getEyeHeight(Pose) 参照 (睡眠オフセット等) も合わせる */
    @SubscribeEvent
    public static void onMaidEyeHeight(EntityEvent.EyeHeight event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (event.getPose() == Pose.SLEEPING) return;
        if (!isMaidSafe(villager)) return;
        event.setNewEyeHeight(villager.isBaby() ? 0.635F : 1.27F);
    }

    /** 胴の着脱で当たり判定を即時更新する (装備変更は Size 再計算を起こさないため) */
    @SubscribeEvent
    public static void onMaidEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (event.getSlot() != EquipmentSlot.CHEST) return;
        villager.refreshDimensions();
    }
}
