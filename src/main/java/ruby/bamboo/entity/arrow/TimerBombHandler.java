package ruby.bamboo.entity.arrow;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 爆発矢の時限爆発タイマーを毎サーバーtick進めるイベントハンドラ。
 * <p>
 * TimerBomb は EntityType を使わない軽量トラッカーなので、ServerTickEvent から
 * {@link TimerBomb#tickAll()} を呼び出す必要がある。(旧 1.10.2 の
 * ITickable 相当)
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TimerBombHandler {

    private TimerBombHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != LogicalSide.SERVER) {
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            TimerBomb.tickAll();
        }
    }
}