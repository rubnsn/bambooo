package ruby.bamboo.handler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.Accessory;

/**
 * アクセサリ類の一括発動ハンドラ (sakura PlayerTickHandler の 1.20.1 移植・拡張枠なし版)。
 * <p>
 * インベントリ (メイン 36 + オフハンド) 内を探し、Accessory 実装品の playerPostTick を呼ぶ。
 * サイド・間引き・発動条件は各アイテム側で制御するため、ここでは生死のみ見る。
 * <p>
 * 1.21: NeoForge の {@code PlayerTickEvent.Post} + {@code getEntity()} (§9。Post は tick 終端発火のため Phase 判定不要)。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AccessoryTickHandler {

    private AccessoryTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
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
