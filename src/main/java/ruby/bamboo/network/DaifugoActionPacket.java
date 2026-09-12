package ruby.bamboo.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.daifugo.DaifugoManager;

/**
 * C→S 大富豪の着手 (空配列=パス)。
 */
public class DaifugoActionPacket {
    public List<Integer> cards = new ArrayList<>();

    public DaifugoActionPacket() {
    }

    public DaifugoActionPacket(List<Integer> cards) {
        this.cards = cards;
    }

    public static void encode(DaifugoActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.cards.stream().mapToInt(Integer::intValue).toArray());
    }

    public static DaifugoActionPacket decode(FriendlyByteBuf buf) {
        DaifugoActionPacket msg = new DaifugoActionPacket();
        for (int id : buf.readVarIntArray()) {
            msg.cards.add(id);
        }
        return msg;
    }

    public static void handle(DaifugoActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.action(player.getServer(), player, msg.cards);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
