package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.BambooMod;
import ruby.bamboo.handler.FishingHandler;

/**
 * C→S 釣り結果報告。success=0, fail=1, cancel=2。
 *
 * <p>1.21.1 NeoForge: CustomPacketPayload + StreamCodec 形式 (WishRequestPacket の先例)。
 */
public class FishingResultPacket implements CustomPacketPayload {

    public static final int SUCCESS = 0;
    public static final int FAIL = 1;
    public static final int CANCEL = 2;

    public static final CustomPacketPayload.Type<FishingResultPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "fishing_result"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, FishingResultPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), FishingResultPacket::decode);

    private final int resultType;

    public FishingResultPacket(int resultType) {
        this.resultType = resultType;
    }

    public int getResultType() {
        return resultType;
    }

    public static void encode(FishingResultPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.resultType);
    }

    public static FishingResultPacket decode(FriendlyByteBuf buf) {
        int t = buf.readByte();
        return new FishingResultPacket(t);
    }

    public static void handle(FishingResultPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (msg.resultType < 0 || msg.resultType > 2) {
                BambooMod.LOGGER.warn("Invalid fishing result type {}", msg.resultType);
                return;
            }
            FishingHandler.handleResult(player, msg.resultType);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
