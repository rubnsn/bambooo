package ruby.bamboo.client.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import ruby.bamboo.client.gui.FishingMinigameScreen;
import ruby.bamboo.client.gui.FishingPowerGaugeScreen;
import ruby.bamboo.network.FishingCastResultPacket;

/**
 * クライアント側 釣りハンドラ。
 * 現在はパワーゲージと仮想GUIミミニゲームの Screen 管理のみを担う。
 *
 * <p>1.21.1 NeoForge: @EventBusSubscriber は不要 (ハンドラメソッドを持たないため)。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFishingHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientFishingHandler() {
    }

    public static boolean isFishing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FishingPowerGaugeScreen) return true;
        if (mc.screen instanceof FishingMinigameScreen) return true;
        return false;
    }

    public static void openPowerGauge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;
        if (isFishing()) return;
        mc.setScreen(new FishingPowerGaugeScreen());
    }

    public static void handleCastResult(FishingCastResultPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            // パワーゲージは既に閉じられている想定。ミニゲーム GUI を開く
            // 既存の他の Screen が開いていれば置換する (power gauge は既に閉じている)
            mc.setScreen(new FishingMinigameScreen(pkt));
        });
    }
}
