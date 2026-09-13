package ruby.bamboo.client.handler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 受信チャットの保持。ミニゲーム画面の左下にバニラ風のフェード付きで表示する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientChatLog {
    private static final int CAP = 12;
    private static final Deque<Entry> LOG = new ArrayDeque<>();

    /** 受信文と受信時刻 (guiTicks)。 */
    public record Entry(Component text, long tick) {
    }

    private ClientChatLog() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        LOG.addLast(new Entry(event.getMessage(), Minecraft.getInstance().gui.getGuiTicks()));
        while (LOG.size() > CAP) {
            LOG.removeFirst();
        }
    }

    public static List<Entry> entries() {
        return new ArrayList<>(LOG);
    }
}
