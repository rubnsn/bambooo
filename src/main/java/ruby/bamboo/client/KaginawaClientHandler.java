package ruby.bamboo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.KaginawaHookEntity;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.KaginawaInputPacket;
import ruby.bamboo.network.KaginawaStateManager;

/**
 * クライアント側入力ポーリング → サーバへパケ送信。
 * Shift=伸長, Space=巻取り, Space+W=牽引, WASD=振り子, Sprint=加速
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaginawaClientHandler {

    private KaginawaClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }
        // フックが無ければ送信不要 (クライアント側のStateManagerは未同期なのでentityの存在で判定)
        // サーバ側hookをクライアントが知る方法: 近傍のKaginawaHookEntityを検索
        KaginawaHookEntity hook = findHookForPlayer(player);
        if (hook == null || !hook.isAnchored()) {
            return;
        }

        // キー取得
        boolean shift = mc.options.keyShift.isDown();
        boolean jump = mc.options.keyJump.isDown();
        boolean up = mc.options.keyUp.isDown();
        boolean down = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean sprint = mc.options.keySprint.isDown() || player.isSprinting();

        float forward = 0;
        if (up) forward += 1;
        if (down) forward -= 1;
        float side = 0;
        if (right) side += 1;
        if (left) side -= 1;

        // reelDir: Shift=1 (伸長), Space=-1 (巻取り)
        // pull はスペース+W牽引だが紛らわしいため常時無効
        boolean pull = false;

        // 地面判定は onGround()/getOnPos() ではなく POS Y-1(足元-0.1)の実ブロックで (noGravityで信用できない)
        net.minecraft.core.BlockPos belowPos = net.minecraft.core.BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        boolean onSolidGround = !player.level().getBlockState(belowPos).isAir();
        byte reelDir = 0;
        if (shift && !onSolidGround) {
            reelDir = 1;
        } else if (jump) {
            reelDir = -1;
        }

        // 入力が一切無ければ送信しない (省パケット)
        if (reelDir == 0 && !pull && forward == 0 && side == 0) {
            return;
        }

        KaginawaInputPacket pkt = new KaginawaInputPacket(reelDir, pull, forward, side, sprint);
        BambooNetwork.CHANNEL.sendToServer(pkt);
        // クライアント予測: サーバと同じ pending をローカルにも反映してデシンク防止
        hook.handleInput(reelDir, pull, forward, side, sprint);
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        KaginawaHookEntity hook = findHookForPlayer(player);
        if (hook == null || !hook.isAnchored()) {
            return;
        }
        // フック中はバニラジャンプより巻取りを優先: Input.jumping を潰す
        // mc.options.keyJump.isDown() は物理キー状態なので維持し、Input側だけfalseにして当クラスのreel送信は維持
        boolean jumpDown = false;
        try {
            jumpDown = event.getInput().jumping;
        } catch (Exception ignored) {
            // official/mojmap では jumping、srg では f_108572_
            jumpDown = false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyJump.isDown() || jumpDown) {
            event.getInput().jumping = false;
            player.input.jumping = false;
        }
    }

    private static KaginawaHookEntity findHookForPlayer(Player player) {
        // クライアント側ではStateManagerに登録されていないため、レベル内のentityを検索
        // 範囲 64m
        try {
            for (KaginawaHookEntity e : player.level().getEntitiesOfClass(KaginawaHookEntity.class, player.getBoundingBox().inflate(64))) {
                Player owner = e.getOwnerPlayer();
                if (owner != null && owner.getUUID().equals(player.getUUID())) {
                    return e;
                }
                // owner取れない場合、距離とanchoredで推定
                if (owner == null && e.isAnchored() && e.distanceTo(player) < 32) {
                    return e;
                }
            }
        } catch (Exception ignored) {
        }
        // fallback: StateManager (サーバ同期があれば)
        return KaginawaStateManager.getHook(player);
    }
}
