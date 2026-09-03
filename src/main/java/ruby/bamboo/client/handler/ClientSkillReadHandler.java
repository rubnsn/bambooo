package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.ReadingScreen;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.SkillReadCancelPacket;

/**
 * クライアント側 読書画面管理。
 * サーバー閉屏と自発閉屏を区別し、自発時のみキャンセルを送る。
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
        BambooNetwork.CHANNEL.sendToServer(new SkillReadCancelPacket());
    }
}
