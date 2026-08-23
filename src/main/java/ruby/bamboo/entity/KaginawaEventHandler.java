package ruby.bamboo.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.network.KaginawaStateManager;

/**
 * 鈎縄関連イベント: 落下軽減、ログアウト/死亡時のフック回収
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaginawaEventHandler {

    private KaginawaEventHandler() {
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (KaginawaStateManager.hasHook(player)) {
            var hook = KaginawaStateManager.getHook(player);
            if (hook != null && hook.isAnchored()) {
                // フック中は落下ダメージ無効
                event.setCanceled(true);
                player.fallDistance = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        var hook = KaginawaStateManager.getHook(player);
        if (hook != null) {
            hook.discardWithCleanup();
        }
        KaginawaStateManager.remove(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        var hook = KaginawaStateManager.getHook(player);
        if (hook != null) {
            hook.discardWithCleanup();
        }
        KaginawaStateManager.remove(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        var hook = KaginawaStateManager.getHook(player);
        if (hook != null) {
            hook.discardWithCleanup();
        }
        KaginawaStateManager.remove(player);
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isAnchoredHook(player)) {
            return;
        }
        // 地上でのバニラジャンプを潰して巻取りを優先 (LivingJumpEventはCancel不可なので速度を戻す)
        Vec3 m = player.getDeltaMovement();
        if (m.y > 0) {
            // ジャンプパワー分を打ち消し、水平は維持
            player.setDeltaMovement(m.x, 0, m.z);
            player.hasImpulse = false;
        }
        player.fallDistance = 0;
    }

    private static boolean isAnchoredHook(Player player) {
        var hook = KaginawaStateManager.getHook(player);
        if (hook != null && hook.isAnchored()) {
            return true;
        }
        // クライアント側フォールバック: 近傍エンティティ走査 (StateManagerがクライアントで空の場合)
        try {
            for (ruby.bamboo.entity.KaginawaHookEntity e : player.level().getEntitiesOfClass(ruby.bamboo.entity.KaginawaHookEntity.class, player.getBoundingBox().inflate(64))) {
                var owner = e.getOwnerPlayer();
                if (owner != null && owner.getUUID().equals(player.getUUID()) && e.isAnchored()) {
                    return true;
                }
                if (owner == null && e.isAnchored() && e.distanceTo(player) < 32) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        boolean anchored = isAnchoredHook(player);
        if (!anchored) {
            if (player.isNoGravity() && !player.getAbilities().flying && !player.isSpectator() && !player.isFallFlying()) {
                player.setNoGravity(false);
            }
            if (((net.minecraft.world.entity.LivingEntity) player).shouldDiscardFriction()) {
                ((net.minecraft.world.entity.LivingEntity) player).setDiscardFriction(false);
            }
            return;
        }
        player.setNoGravity(true);
        ((net.minecraft.world.entity.LivingEntity) player).setDiscardFriction(true);
        player.fallDistance = 0;
        if (player.tickCount % 20 == 0) {
            ruby.bamboo.BambooMod.LOGGER.info("[Kaginawa] PlayerTick cur={} onGround={} noGravity={} discardFriction={} pos={}", player.getDeltaMovement(), player.onGround(), player.isNoGravity(), ((net.minecraft.world.entity.LivingEntity) player).shouldDiscardFriction(), player.position());
        }
    }
}
