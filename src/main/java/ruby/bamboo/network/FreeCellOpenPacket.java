package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientFreeCellHandler;

/**
 * S→C フリーセル画面を開かせる (テスト用)。
 */
public class FreeCellOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreeCellOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "freecell_open"));

    public static final StreamCodec<FriendlyByteBuf, FreeCellOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), FreeCellOpenPacket::decode);

    public static void encode(FreeCellOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static FreeCellOpenPacket decode(FriendlyByteBuf buf) {
        return new FreeCellOpenPacket();
    }

    public static void handle(FreeCellOpenPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientFreeCellHandler.open());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
