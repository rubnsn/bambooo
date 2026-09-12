package ruby.bamboo.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.daifugo.DaifugoManager;

/**
 * C→S 大富豪のお返し選択 (TRIBUTE中の上位席)。
 */
public class DaifugoTributePacket {
    public List<Integer> cards = new ArrayList<>();

    public DaifugoTributePacket() {
    }

    public DaifugoTributePacket(List<Integer> cards) {
        this.cards = cards;
    }

    public static void encode(DaifugoTributePacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.cards.stream().mapToInt(Integer::intValue).toArray());
    }

    public static DaifugoTributePacket decode(FriendlyByteBuf buf) {
        DaifugoTributePacket msg = new DaifugoTributePacket();
        for (int id : buf.readVarIntArray()) {
            msg.cards.add(id);
        }
        return msg;
    }

    public static void handle(DaifugoTributePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.tribute(player.getServer(), player, msg.cards);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
