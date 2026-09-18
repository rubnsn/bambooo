package ruby.bamboo.gacha;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * ガチャpendingの後始末。未受取のまま切断・停止したら破棄 (複製防止のため復元しない)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GachaServerHandler {
    private GachaServerHandler() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            GachaManager.onLogout(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        GachaManager.clear();
    }
}
