package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S ラウンド決着の申告。seqは配札ごとの通番で、重複・遅延パケットを捨てる。
 * サーバーは必ず残高応答を返す (黙殺しない)。
 */
public class BlackjackSettlePacket {
    public int bet = 100;
    public int outcome = 0;
    public long seq = 0;

    public BlackjackSettlePacket() {
    }

    public BlackjackSettlePacket(int bet, int outcome, long seq) {
        this.bet = bet;
        this.outcome = outcome;
        this.seq = seq;
    }

    public static void encode(BlackjackSettlePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.bet);
        buf.writeVarInt(msg.outcome);
        buf.writeVarLong(msg.seq);
    }

    public static BlackjackSettlePacket decode(FriendlyByteBuf buf) {
        return new BlackjackSettlePacket(buf.readVarInt(), buf.readVarInt(),
                buf.readVarLong());
    }

    public static void handle(BlackjackSettlePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                BlackjackManager.settle(player.getServer(), player, msg.bet, msg.outcome,
                        msg.seq);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
