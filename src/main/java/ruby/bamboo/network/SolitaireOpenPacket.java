package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientSolitaireHandler;

/**
 * S→C ソリティア画面を開かせる (テスト用)。
 */
public class SolitaireOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SolitaireOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "solitaire_open"));

    public static final StreamCodec<FriendlyByteBuf, SolitaireOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SolitaireOpenPacket::decode);

    public static void encode(SolitaireOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static SolitaireOpenPacket decode(FriendlyByteBuf buf) {
        return new SolitaireOpenPacket();
    }

    public static void handle(SolitaireOpenPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientSolitaireHandler.open());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
