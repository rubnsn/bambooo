package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.BambooMod;
import ruby.bamboo.handler.WishEventHandler;

/**
 * C→S 願い送信パケット。最大30文字。
 */
public class WishRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WishRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "wish_request"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, WishRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), WishRequestPacket::decode);

    private final String wish;

    public WishRequestPacket(String wish) {
        this.wish = wish;
    }

    public String getWish() {
        return wish;
    }

    public static void encode(WishRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.wish, 30);
    }

    public static WishRequestPacket decode(FriendlyByteBuf buf) {
        String s = buf.readUtf(30);
        return new WishRequestPacket(s);
    }

    public static void handle(WishRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!WishEventHandler.validateAndConsumePending(player)) {
                BambooMod.LOGGER.warn("Wish request without valid pending from {}", player.getName().getString());
                return;
            }
            String raw = msg.wish;
            if (raw == null) {
                raw = "";
            }
            raw = raw.trim();
            if (raw.length() > 30) {
                raw = raw.substring(0, 30);
            }
            raw = raw.replaceAll("\\p{Cntrl}", "");
            BambooMod.LOGGER.info("Wish received from {}: '{}'", player.getName().getString(), raw);
            ruby.bamboo.core.wish.WishManager.resolveAndExecute(player, raw);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
