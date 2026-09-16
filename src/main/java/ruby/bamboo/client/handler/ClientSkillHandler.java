package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.StatusBookScreen;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * クライアント側 スキル受信。ログイン時同期済みデータを自 Attachment へ反映する。
 *
 * <p>1.21.1 NeoForge: 旧 Cap 参照を Player Attachment
 * ({@code player.getData(BambooCapabilities.SKILL)}) に置換。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkillHandler {

    private ClientSkillHandler() {
    }

    public static void handleSync(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            mc.player.getData(BambooCapabilities.SKILL).deserializeNBT(tag);
        });
    }

    public static void openStatus() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            mc.setScreen(new StatusBookScreen());
        });
    }
}
