package ruby.bamboo.core.wish;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import ruby.bamboo.BambooMod;

/**
 * 願いカウントの永続・起床減衰。
 * 死亡・リログで維持 (Attachment serialize + copyOnDeath)。ベッド起床で1減少 (0止まり)。
 * 布団は夜間睡眠をバニラ BedBlock に委譲するため PlayerWakeUpEvent で拾える。
 * <p>
 * 1.21.1 NeoForge: 旧 Clone コピー + Cap NBT 保存は不要
 * (copyOnDeath が死亡時コピーを担う)。起床減衰のみ残す。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WishEvents {

    private WishEvents() {
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        WishHelper.decrement(sp);
    }
}
