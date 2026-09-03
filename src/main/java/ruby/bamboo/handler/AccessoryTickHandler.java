package ruby.bamboo.handler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.Accessory;

/**
 * アクセサリ類の一括発動ハンドラ (sakura PlayerTickHandler の 1.20.1 移植・拡張枠なし版)。
 * <p>
 * インベントリ (メイン 36 + オフハンド) 内を探し、Accessory 実装品の playerPostTick を呼ぶ。
 * サイド・間引き・発動条件は各アイテム側で制御するため、ここでは Phase と生死のみ見る。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccessoryTickHandler {

    private AccessoryTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        Player player = event.player;
        if (!player.isAlive()) {
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() instanceof Accessory accessory) {
                accessory.playerPostTick(player, stack);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() instanceof Accessory accessory) {
            accessory.playerPostTick(player, offhand);
        }
    }
}
