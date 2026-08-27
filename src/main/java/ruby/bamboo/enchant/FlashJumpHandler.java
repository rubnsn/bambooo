package ruby.bamboo.enchant;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.NinjaBraceletItem;

/**
 * 閃跳ハンドラ（腕輪所持で発動）。
 * 空中で1回のみ発動、着地でリセット、クール無し、耐久消費無し。
 * <p>
 * ジャンプキー検出はバニラの `player.jumping` だけでは空中での rising edge が
 * サーバで安定しないため、クライアントの {@link ruby.bamboo.client.FlashJumpClientHandler}
 * が `input.jumping` の rising edge を検出し {@link ruby.bamboo.network.FlashJumpPacket}
 * でサーバへ通知。サーバはパケットで受信した forward/strafe を用いて実行する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlashJumpHandler {

    private static final String TAG_FLASHED = "bamboomod:flash_used";
    private static final String TAG_AIR_TICKS = "bamboomod:flash_air_ticks";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return; // クライアントは FlashJumpClientHandler で処理
        if (player.isSpectator() || player.isCreative() && player.getAbilities().flying) return;

        // 着地でリセット（サーバ側の airTicks と flashed はパケット処理でも参照）
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
            player.getPersistentData().putBoolean(TAG_FLASHED, false);
            player.getPersistentData().putInt(TAG_AIR_TICKS, 0);
            return;
        }

        // 空中カウントを進める（handlePacket で airTicks < 3 チェックに利用）
        int airTicks = player.getPersistentData().getInt(TAG_AIR_TICKS) + 1;
        player.getPersistentData().putInt(TAG_AIR_TICKS, airTicks);
    }

    /**
     * パケット受信時にサーバで実行。WASD入力方向へ閃跳。
     */
    public static void handlePacket(net.minecraft.server.level.ServerPlayer player, float forward, float strafe) {
        if (player.isSpectator() || player.isCreative() && player.getAbilities().flying) return;
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) return;
        if (player.getPersistentData().getBoolean(TAG_FLASHED)) return;
        if (!hasFlashJump(player)) return;
        int airTicks = player.getPersistentData().getInt(TAG_AIR_TICKS);
        if (airTicks < 3) return; // 地上離脱直後の誤爆防止（旧5→3に緩和）
        doFlashJump(player, forward, strafe);
    }

    static void doFlashJump(Player player, float forward, float strafe) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 dir;
        if (forward == 0.0F && strafe == 0.0F) {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0, look.z).normalize();
            // 入力無しは真上に少し寄せる
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

    public static boolean hasFlashJump(Player player) {
        return player.getMainHandItem().getItem() instanceof NinjaBraceletItem
                || player.getOffhandItem().getItem() instanceof NinjaBraceletItem;
    }
}
