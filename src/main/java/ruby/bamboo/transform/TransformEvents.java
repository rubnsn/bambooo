package ruby.bamboo.transform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.network.TransformSyncPacket;

/**
 * 変身 Attachment の永続・再解決・同期。
 * 死亡時は維持する (人間戻しは願いで行う)。
 *
 * <p>1.21.1 NeoForge: データ複写は AttachmentType の {@code copyOnDeath}
 * (死亡時) と通常の respawn/次元移動時の引継ぎに任せ、Clone ハンドラでは
 * 属性キャッシュの破棄のみ行う。旧 {@code reviveCaps} は不要。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TransformEvents {

    private TransformEvents() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
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
        PacketDistributor.sendToPlayer(watcher, new TransformSyncPacket(target.getUUID(), id));
    }
}
