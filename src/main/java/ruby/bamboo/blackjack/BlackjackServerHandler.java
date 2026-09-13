package ruby.bamboo.blackjack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * ベットモードのサーバー側後始末 (切断・停止時は没収=破棄)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
