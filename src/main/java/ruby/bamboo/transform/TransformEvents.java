package ruby.bamboo.transform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.TransformSyncPacket;

/**
 * 変身 Cap の永続・再解決・同期。
 * 死亡時は維持する (人間戻しは願いで行う)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TransformEvents {

    private TransformEvents() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(BambooCapabilities.TRANSFORM).ifPresent(old -> {
            event.getEntity().getCapability(BambooCapabilities.TRANSFORM).ifPresent(nu -> {
                nu.deserializeNBT(old.serializeNBT());
            });
        });
        if (event.getEntity() instanceof ServerPlayer sp) {
            TransformHelper.dropCache(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TransformHelper.revalidate(sp);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TransformHelper.dropCache(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TransformHelper.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TransformHelper.revalidate(sp);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        String id = TransformHelper.resolvedId(target);
        BambooNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> watcher),
                new TransformSyncPacket(target.getUUID(), id));
    }
}
