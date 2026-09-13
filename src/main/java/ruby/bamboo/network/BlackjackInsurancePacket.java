package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S インシュランス申告 (掛け金の半分・1ラウンド1回)。
 * dealerBj=trueならBJ確定で2:1、falseなら没収。
 * サーバーは必ず残高応答を返す。
 */
public class BlackjackInsurancePacket {
    public int bet = 50;
    public boolean dealerBj;
    public long seq = 0;

    public BlackjackInsurancePacket() {
    }

    public BlackjackInsurancePacket(int bet, boolean dealerBj, long seq) {
        this.bet = bet;
        this.dealerBj = dealerBj;
        this.seq = seq;
    }

    public static void encode(BlackjackInsurancePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.bet);
        buf.writeBoolean(msg.dealerBj);
        buf.writeVarLong(msg.seq);
    }

    public static BlackjackInsurancePacket decode(FriendlyByteBuf buf) {
        return new BlackjackInsurancePacket(buf.readVarInt(), buf.readBoolean(),
                buf.readVarLong());
    }

    public static void handle(BlackjackInsurancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                BlackjackManager.insurance(player.getServer(), player, msg.bet,
                        msg.dealerBj, msg.seq);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
