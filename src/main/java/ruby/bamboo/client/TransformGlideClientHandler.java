package ruby.bamboo.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.handler.ClientTransformHandler;
import ruby.bamboo.network.TransformGlidePacket;
import ruby.bamboo.transform.TransformRegistry;

/**
 * 滑空トグルのクライアント側入力検出 + 落下予測。
 * FlashJumpClientHandler と同じく input.jumping の rising edge を使う。
 * 地上ジャンプの立ち上がりでは送らない (airTicks >= 3)。
 * サーバのみの減速ではローカルの落下予測が上書きするため、
 * トグル mirror を持ち、クライアント側でも同じ減速を即時適用する。
 *
 * <p>1.21.1 NeoForge: {@code PlayerTickEvent.Post} + NeoForge PacketDistributor
 * へ移行。中身は原文のまま。
 */
@EventBusSubscriber(modid = BambooMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class TransformGlideClientHandler {

    private static final String TAG_WAS_JUMPING = "bamboomod:glide_was_jumping";
    private static final String TAG_AIR_TICKS = "bamboomod:glide_air_ticks";

    /** トグルのクライアント mirror (既定OFF。地上でOFF・空中スペースで反転)。 */
    private static final Map<UUID, Boolean> MIRROR = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_ID = new ConcurrentHashMap<>();

    public static boolean isGlideOn(UUID uuid) {
        return MIRROR.getOrDefault(uuid, false);
    }

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            return;
        }
        if (!(player instanceof LocalPlayer localPlayer)) {
            return;
        }
        if (player.isSpectator() || player.isCreative() && player.getAbilities().flying) {
            return;
        }
        var input = localPlayer.input;
        boolean isJumping = input != null && input.jumping;

        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
            player.getPersistentData().putBoolean(TAG_WAS_JUMPING, isJumping);
            player.getPersistentData().putInt(TAG_AIR_TICKS, 0);
            MIRROR.put(player.getUUID(), false);
            return;
        }
        int airTicks = player.getPersistentData().getInt(TAG_AIR_TICKS) + 1;
        player.getPersistentData().putInt(TAG_AIR_TICKS, airTicks);

        String id = ClientTransformHandler.get(player.getUUID());
        if (!TransformRegistry.GLIDE.contains(id)) {
            player.getPersistentData().putBoolean(TAG_WAS_JUMPING, isJumping);
            MIRROR.remove(player.getUUID());
            LAST_ID.remove(player.getUUID());
            return;
        }
        // 変身替わりで mirror を既定OFFへ
        String last = LAST_ID.get(player.getUUID());
        if (last == null || !last.equals(id)) {
            LAST_ID.put(player.getUUID(), id);
            MIRROR.put(player.getUUID(), false);
        }
        boolean wasJumping = player.getPersistentData().getBoolean(TAG_WAS_JUMPING);
        player.getPersistentData().putBoolean(TAG_WAS_JUMPING, isJumping);
        if (isJumping && !wasJumping && airTicks >= 3) {
            MIRROR.put(player.getUUID(), !isGlideOn(player.getUUID()));
            PacketDistributor.sendToServer(new TransformGlidePacket());
        }
        // クライアント予測: サーバと同じ減速を即時適用
        if (isGlideOn(player.getUUID())) {
            Vec3 delta = player.getDeltaMovement();
            if (delta.y < -0.2D) {
                player.setDeltaMovement(delta.x * 0.98D, delta.y * 0.6D, delta.z * 0.98D);
                player.fallDistance *= 0.7F;
            }
        }
    }
}
