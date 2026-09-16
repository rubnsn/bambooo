package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.client.gui.ReadingScreen;
import ruby.bamboo.network.SkillReadCancelPacket;

/**
 * クライアント側 読書画面管理。
 * サーバー閉屏と自発閉屏を区別し、自発時のみキャンセルを送る。
 *
 * <p>1.21.1: 送信は {@code PacketDistributor.sendToServer} に置換。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkillReadHandler {

    private static boolean suppressCancel = false;

    private ClientSkillReadHandler() {
    }

    public static void open(String skillId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            suppressCancel = false;
            mc.setScreen(new ReadingScreen(skillId));
        });
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof ReadingScreen) {
                suppressCancel = true;
                mc.setScreen(null);
            }
        });
    }

    public static void onScreenClosed() {
        if (suppressCancel) {
            suppressCancel = false;
            return;
        }
        PacketDistributor.sendToServer(new SkillReadCancelPacket());
    }
}
