package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.handler.FishingHandler;

/**
 * C→S キャストリクエスト。パワーゲージで決定した距離 (4-15) をサーバーへ送信する。
 *
 * <p>1.21.1 NeoForge: CustomPacketPayload + StreamCodec 形式 (WishRequestPacket の先例)。
 */
public class FishingCastRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FishingCastRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "fishing_cast_request"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, FishingCastRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), FishingCastRequestPacket::decode);

    private final int distance;

    public FishingCastRequestPacket(int distance) {
        this.distance = distance;
    }

    public int getDistance() {
        return distance;
    }

    public static void encode(FishingCastRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.distance);
    }

    public static FishingCastRequestPacket decode(FriendlyByteBuf buf) {
        int d = buf.readVarInt();
        return new FishingCastRequestPacket(d);
    }

    public static void handle(FishingCastRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            int d = msg.distance;
            if (d < 4) d = 4;
            if (d > 15) d = 15;
            FishingHandler.handleCastRequest(player, d);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
