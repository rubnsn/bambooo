package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.ClientWishHandler;

/**
 * S→C 願い入力画面を開けパケット。payloadなし。
 */
public class WishOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WishOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "wish_open"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, WishOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), WishOpenPacket::decode);

    public WishOpenPacket() {
    }

    public static void encode(WishOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static WishOpenPacket decode(FriendlyByteBuf buf) {
        return new WishOpenPacket();
    }

    public static void handle(WishOpenPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientWishHandler.handleOpen());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
