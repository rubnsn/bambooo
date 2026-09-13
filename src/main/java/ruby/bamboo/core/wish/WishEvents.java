package ruby.bamboo.core.wish;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * 願いカウントの永続・起床減衰。
 * 死亡・リログで維持 (Clone コピー + Cap NBT 保存)。ベッド起床で1減少(0止まり)。
 * 布団は夜間睡眠をバニラ BedBlock に委譲するため PlayerWakeUpEvent で拾える。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WishEvents {

    private WishEvents() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            try {
                event.getOriginal().reviveCaps();
            } catch (Exception ignored) {
            }
        }
        event.getOriginal().getCapability(BambooCapabilities.WISH).ifPresent(old -> {
            event.getEntity().getCapability(BambooCapabilities.WISH).ifPresent(nu -> {
                nu.deserializeNBT(old.serializeNBT());
            });
        });
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        WishHelper.decrement(sp);
    }
}
