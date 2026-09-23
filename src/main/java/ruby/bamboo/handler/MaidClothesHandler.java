package ruby.bamboo.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
        if (!player.isCreative()) {
            held.shrink(1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
