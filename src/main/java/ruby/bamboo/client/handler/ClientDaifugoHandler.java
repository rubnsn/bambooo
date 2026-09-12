package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.daifugo.DaifugoGameScreen;
import ruby.bamboo.client.gui.daifugo.DaifugoLobbyScreen;
import ruby.bamboo.daifugo.DaifugoRoom;
import ruby.bamboo.daifugo.DaifugoSnapshot;

/**
 * クライアント側 大富豪スナップショット受信。状態に応じて画面を開く・更新する。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientDaifugoHandler {

    private ClientDaifugoHandler() {
    }

    public static void onSnapshot(DaifugoSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            Screen current = mc.screen;
            if (snapshot.state == DaifugoRoom.State.LOBBY.ordinal()) {
                if (current instanceof DaifugoLobbyScreen lobby) {
                    lobby.update(snapshot);
                } else {
                    mc.setScreen(new DaifugoLobbyScreen(snapshot));
                }
            } else {
                if (current instanceof DaifugoGameScreen game) {
                    game.update(snapshot);
                } else {
                    mc.setScreen(new DaifugoGameScreen(snapshot));
                }
            }
        });
    }
}
