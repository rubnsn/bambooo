package ruby.bamboo.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.enchant.FlashJumpHandler;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FlashJumpPacket;

/**
 * 閃跳のクライアント側入力検出。
 * `input.jumping` の rising edge を検出し、WASD方向をパケットでサーバへ送信。
 * クライアントでは即時予測で push し、サーバはパケットで確定する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlashJumpClientHandler {

    private static final String TAG_FLASHED = "bamboomod:flash_used";
    private static final String TAG_AIR_TICKS = "bamboomod:flash_air_ticks";
    private static final String TAG_WAS_JUMPING = "bamboomod:was_jumping_client";

    @SubscribeEvent
    public static void onClientPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (!player.level().isClientSide) return;
        if (!(player instanceof LocalPlayer localPlayer)) return;
        if (player.isSpectator() || player.isCreative() && player.getAbilities().flying) return;

        // 入力取得
        var input = localPlayer.input;
        boolean isJumping = input != null && input.jumping;
        float forward = input != null ? input.forwardImpulse : 0.0F;
        float strafe = input != null ? input.leftImpulse : 0.0F;

        // 着地でリセット
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
            player.getPersistentData().putBoolean(TAG_FLASHED, false);
            player.getPersistentData().putBoolean(TAG_WAS_JUMPING, isJumping);
            player.getPersistentData().putInt(TAG_AIR_TICKS, 0);
            return;
        }

        // 空中カウント
        int airTicks = player.getPersistentData().getInt(TAG_AIR_TICKS) + 1;
        player.getPersistentData().putInt(TAG_AIR_TICKS, airTicks);

        if (player.getPersistentData().getBoolean(TAG_FLASHED)) return;
        if (!FlashJumpHandler.hasFlashJump(player)) return;

        boolean wasJumping = player.getPersistentData().getBoolean(TAG_WAS_JUMPING);
        player.getPersistentData().putBoolean(TAG_WAS_JUMPING, isJumping);
        if (!isJumping || wasJumping) return; // rising edge のみ
        if (airTicks < 3) return;

        // クライアント予測で即時 push
        doClientFlashJump(player, forward, strafe);
        // サーバへ通知
        BambooNetwork.CHANNEL.sendToServer(new FlashJumpPacket(forward, strafe));
    }

    private static void doClientFlashJump(Player player, float forward, float strafe) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 dir;
        if (forward == 0.0F && strafe == 0.0F) {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0, look.z).normalize();
            if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(0, 1, 0);
        } else {
            float yawRad = player.getYRot() * (float) Math.PI / 180F;
            Vec3 forwardVec = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
            Vec3 strafeVec = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();
            dir = forwardVec.scale(forward).add(strafeVec.scale(strafe)).normalize();
        }
        double pushX = dir.x * 0.6;
        double pushZ = dir.z * 0.6;
        double pushY = 0.4;
        player.setDeltaMovement(motion.add(pushX, pushY, pushZ));
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.getPersistentData().putBoolean(TAG_FLASHED, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}
