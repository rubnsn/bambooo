package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * クライアント側 スキル受信。ログイン時同期済みデータを自 Cap へ反映する。
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
            mc.player.getCapability(BambooCapabilities.SKILL).ifPresent(s -> s.deserializeNBT(tag));
        });
    }
}
