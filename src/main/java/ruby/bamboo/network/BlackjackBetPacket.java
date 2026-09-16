package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S ベットモードの入金 (エメラルド→点)。
 */
public class BlackjackBetPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackjackBetPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_bet"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackBetPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackBetPacket::decode);

    public int emeralds = 1;

    public BlackjackBetPacket() {
    }

    public BlackjackBetPacket(int emeralds) {
        this.emeralds = emeralds;
    }

    public static void encode(BlackjackBetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.emeralds);
    }

    public static BlackjackBetPacket decode(FriendlyByteBuf buf) {
        return new BlackjackBetPacket(buf.readVarInt());
    }

    public static void handle(BlackjackBetPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                BlackjackManager.bet(player.getServer(), player, msg.emeralds);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
