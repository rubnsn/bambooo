package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S 終了 (点→エメラルド換金)。
 */
public class BlackjackCashoutPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlackjackCashoutPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_cashout"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackCashoutPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackCashoutPacket::decode);

    public static void encode(BlackjackCashoutPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackCashoutPacket decode(FriendlyByteBuf buf) {
        return new BlackjackCashoutPacket();
    }

    public static void handle(BlackjackCashoutPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                BlackjackManager.cashout(player.getServer(), player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
