package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.trump.BlackjackScreen;
import ruby.bamboo.network.BlackjackBalancePacket;

/**
 * クライアント側 ブラックジャック画面の起動・残高受信 (テスト用)。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientBlackjackHandler {

    private ClientBlackjackHandler() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            mc.setScreen(new BlackjackScreen());
        });
    }

    public static void balance(BlackjackBalancePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof BlackjackScreen screen) {
                screen.onBalance(msg.balance, msg.active, msg.paid, msg.remainder,
                        msg.error);
            }
        });
    }
}
