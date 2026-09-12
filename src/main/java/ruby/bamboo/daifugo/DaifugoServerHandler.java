package ruby.bamboo.daifugo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import ruby.bamboo.BambooMod;

/**
 * 大富豪のサーバー側駆動 (CPU・遷移・切断代打・停止時破棄)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DaifugoServerHandler {
    private DaifugoServerHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        DaifugoManager.tick(server);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MinecraftServer server = sp.getServer();
            if (server != null) {
                DaifugoManager.onLogout(sp.getUUID(), server);
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        DaifugoManager.clear();
    }
}
