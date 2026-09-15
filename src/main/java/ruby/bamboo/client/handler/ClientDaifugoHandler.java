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
 * ESC等で意図的に閉じた後は、大富豪札の使用 (再開指示) があるまで自動で
 * 開き直さない。対戦中はサーバーが毎秒スナップショットを送るため、
 * 無条件に開くと閉じても1秒で復活してしまう。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientDaifugoHandler {

    private ClientDaifugoHandler() {
    }

    /** 意図的に閉じたまま (札使用で解除)。 */
    private static boolean userClosed = false;
    /** 大富豪札を使った直後 (次のスナップショットで必ず開く)。 */
    private static boolean expectOpen = false;

    /** 画面を閉じたときに呼ぶ (ESC・退出ボタン)。 */
    public static void noteClosed() {
        userClosed = true;
        expectOpen = false;
    }

    /** 大富豪札の使用時に呼ぶ (再開指示)。 */
    public static void expectOpen() {
        expectOpen = true;
    }

    public static void onSnapshot(DaifugoSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            Screen current = mc.screen;
            if (current instanceof DaifugoLobbyScreen lobby
                    || current instanceof DaifugoGameScreen) {
                // 開いている画面の更新 (ロビー→対戦の切替含む)
                if (snapshot.state == DaifugoRoom.State.LOBBY.ordinal()) {
                    if (current instanceof DaifugoLobbyScreen lobbyScreen) {
                        lobbyScreen.update(snapshot);
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
                return;
            }
            if (expectOpen) {
                expectOpen = false;
                userClosed = false;
            } else if (userClosed) {
                // 意図的に閉じたまま。札使用で再開する。
                return;
            }
            if (snapshot.state == DaifugoRoom.State.LOBBY.ordinal()) {
                mc.setScreen(new DaifugoLobbyScreen(snapshot));
            } else {
                mc.setScreen(new DaifugoGameScreen(snapshot));
            }
        });
    }
}
