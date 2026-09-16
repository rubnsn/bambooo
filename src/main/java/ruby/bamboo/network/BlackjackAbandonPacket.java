package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S 終了せずに閉じた (没収)。
 */
public class BlackjackAbandonPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlackjackAbandonPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_abandon"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackAbandonPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackAbandonPacket::decode);

    public static void encode(BlackjackAbandonPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackAbandonPacket decode(FriendlyByteBuf buf) {
        return new BlackjackAbandonPacket();
    }

    public static void handle(BlackjackAbandonPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                BlackjackManager.abandon(player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
