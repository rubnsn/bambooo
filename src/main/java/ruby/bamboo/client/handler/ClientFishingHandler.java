package ruby.bamboo.client.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import ruby.bamboo.BambooMod;
import ruby.bamboo.client.gui.FishingMinigameScreen;
import ruby.bamboo.client.gui.FishingPowerGaugeScreen;
import ruby.bamboo.network.FishingCastResultPacket;

/**
 * クライアント側 釣りハンドラ。
 * 現在はパワーゲージと仮想GUIミミニゲームの Screen 管理のみを担う。
 * 旧 HUD 直描画方式は FishingMinigameScreen へ移行した。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientFishingHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

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
