package ruby.bamboo.blackjack;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;

/**
 * ベットモードのサーバー側後始末 (切断・停止時は没収=破棄)。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BlackjackServerHandler {
    private BlackjackServerHandler() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            BlackjackManager.onLogout(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        BlackjackManager.clear();
    }
}
