package ruby.bamboo.entity.arrow;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;

/**
 * 爆発矢の時限爆発タイマーを毎サーバーtick進めるイベントハンドラ。
 * <p>
 * TimerBomb は EntityType を使わない軽量トラッカーなので、ServerTickEvent から
 * {@link TimerBomb#tickAll()} を呼び出す必要がある。(旧 1.10.2 の
 * ITickable 相当)
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TimerBombHandler {

    private TimerBombHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TimerBomb.tickAll();
    }
}