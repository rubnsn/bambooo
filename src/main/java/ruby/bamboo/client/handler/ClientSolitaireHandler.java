package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.trump.SolitaireScreen;

/**
 * クライアント側 ソリティア画面の起動 (テスト用)。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSolitaireHandler {

    private ClientSolitaireHandler() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            mc.setScreen(new SolitaireScreen());
        });
    }
}
