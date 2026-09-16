package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientBlackjackHandler;

/**
 * S→C ブラックジャック画面を開かせる (テスト用)。
 */
public class BlackjackOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlackjackOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_open"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackOpenPacket::decode);

    public static void encode(BlackjackOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackOpenPacket decode(FriendlyByteBuf buf) {
        return new BlackjackOpenPacket();
    }

    public static void handle(BlackjackOpenPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientBlackjackHandler.open());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
