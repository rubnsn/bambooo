package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S インシュランス申告 (掛け金の半分・1ラウンド1回)。
 * dealerBj=trueならBJ確定で2:1、falseなら没収。
 * サーバーは必ず残高応答を返す。
 */
public class BlackjackInsurancePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackjackInsurancePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_insurance"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackInsurancePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackInsurancePacket::decode);

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

    public static void handle(BlackjackInsurancePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                BlackjackManager.insurance(player.getServer(), player, msg.bet,
                        msg.dealerBj, msg.seq);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
