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
 * stairs は [X,JK,JK] リード時の宣言 (true=階段)。
 */
public class DaifugoActionPacket {
    public List<Integer> cards = new ArrayList<>();
    public boolean stairs = false;

    public DaifugoActionPacket() {
    }

    public DaifugoActionPacket(List<Integer> cards) {
        this.cards = cards;
    }

    public DaifugoActionPacket(List<Integer> cards, boolean stairs) {
        this.cards = cards;
        this.stairs = stairs;
    }

    public static void encode(DaifugoActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.cards.stream().mapToInt(Integer::intValue).toArray());
        buf.writeBoolean(msg.stairs);
    }

    public static DaifugoActionPacket decode(FriendlyByteBuf buf) {
        DaifugoActionPacket msg = new DaifugoActionPacket();
        for (int id : buf.readVarIntArray()) {
            msg.cards.add(id);
        }
        msg.stairs = buf.readBoolean();
        return msg;
    }

    public static void handle(DaifugoActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.action(player.getServer(), player, msg.cards, msg.stairs);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
