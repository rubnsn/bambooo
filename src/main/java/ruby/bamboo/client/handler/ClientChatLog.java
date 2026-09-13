package ruby.bamboo.client.handler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 受信チャットの保持。大富豪画面の中段左側に表示するため (バニラのチャット欄とは別)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientChatLog {
    private static final int CAP = 12;
    private static final Deque<Component> LOG = new ArrayDeque<>();

    private ClientChatLog() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        LOG.addLast(event.getMessage());
        while (LOG.size() > CAP) {
            LOG.removeFirst();
        }
    }

    public static List<Component> recent() {
        return new ArrayList<>(LOG);
    }
}
