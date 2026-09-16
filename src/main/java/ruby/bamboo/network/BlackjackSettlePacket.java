package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S ラウンド決着の申告。seqは配札ごとの通番で、重複・遅延パケットを捨てる。
 * サーバーは必ず残高応答を返す (黙殺しない)。
 */
public class BlackjackSettlePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackjackSettlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_settle"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackSettlePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackSettlePacket::decode);

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

    public static void handle(BlackjackSettlePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                BlackjackManager.settle(player.getServer(), player, msg.bet, msg.outcome,
                        msg.seq);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
