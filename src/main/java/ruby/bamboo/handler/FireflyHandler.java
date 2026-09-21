package ruby.bamboo.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.entity.FireflyEntity;

/**
 * ホタルの瓶捕獲。ガラス瓶を持ってホタルに右クリックでホタル瓶化する。
 * (Item#interactLivingEntity は対象側の処理が優先されるためイベント側で処理)
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FireflyHandler {

    private FireflyHandler() {
    }

    @SubscribeEvent
    public static void onCapture(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof FireflyEntity firefly)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ItemStack bottle = new ItemStack(BambooBlocks.FIREFLY_BOTTLE.get());
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
        firefly.discard();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
