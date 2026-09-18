package ruby.bamboo.client.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.GachaOpenScreen;
import ruby.bamboo.client.gui.GachaSpinScreen;
import ruby.bamboo.client.gui.GachaTopScreen;
import ruby.bamboo.gacha.GachaRarity;

/**
 * クライアント側ガチャ進行管理。結果10件を保持し、画面遷移を行う。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGachaHandler {
    private static final List<GachaRarity> RARITIES = new ArrayList<>();
    private static final List<ItemStack> STACKS = new ArrayList<>();
    /** 結果待ち (Top→Spin直後)。結果到着でSpinへ配送 */
    private static boolean waiting;

    private ClientGachaHandler() {
    }

    public static void openTop() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.setScreen(new GachaTopScreen());
            }
        });
    }

    /** Topの10連ボタン→要求送信+Spin待機画面へ。 */
    public static void requestDraw() {
        waiting = true;
        RARITIES.clear();
        STACKS.clear();
        ruby.bamboo.network.BambooNetwork.CHANNEL.sendToServer(
                new ruby.bamboo.network.GachaDrawRequestPacket());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.setScreen(new GachaSpinScreen());
            }
        });
    }

    public static void onResult(List<GachaRarity> rarities, List<ItemStack> stacks) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            RARITIES.clear();
            STACKS.clear();
            if (rarities.isEmpty()) {
                waiting = false;
                if (mc.screen instanceof GachaSpinScreen spin) {
                    spin.onDrawFailed();
                }
                return;
            }
            RARITIES.addAll(rarities);
            for (ItemStack s : stacks) {
                STACKS.add(s.copy());
            }
            waiting = false;
            if (mc.screen instanceof GachaSpinScreen spin) {
                spin.onDrawArrived(List.copyOf(RARITIES));
            }
        });
    }

    public static boolean isWaiting() {
        return waiting;
    }

    public static List<GachaRarity> rarities() {
        return List.copyOf(RARITIES);
    }

    public static List<ItemStack> stacks() {
        List<ItemStack> c = new ArrayList<>();
        for (ItemStack s : STACKS) {
            c.add(s.copy());
        }
        return c;
    }

    /** Spin演出完了→Open画面へ。 */
    public static void openOpenScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && !RARITIES.isEmpty()) {
                mc.setScreen(new GachaOpenScreen(List.copyOf(RARITIES), stacks()));
            }
        });
    }

    /** 全開封→払い出し要求+閉じる。 */
    public static void claimAndClose() {
        ruby.bamboo.network.BambooNetwork.CHANNEL.sendToServer(
                new ruby.bamboo.network.GachaClaimPacket());
        RARITIES.clear();
        STACKS.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof GachaOpenScreen) {
                mc.setScreen(null);
            }
        });
        waiting = false;
    }
}
