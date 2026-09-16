package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.daifugo.DaifugoManager;

/** C→S 大富豪の開始 (オーナーのみ)。 */
public class DaifugoStartPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DaifugoStartPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "daifugo_start"));

    public static final StreamCodec<FriendlyByteBuf, DaifugoStartPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DaifugoStartPacket::decode);

    public static void encode(DaifugoStartPacket msg, FriendlyByteBuf buf) {
    }

    public static DaifugoStartPacket decode(FriendlyByteBuf buf) {
        return new DaifugoStartPacket();
    }

    public static void handle(DaifugoStartPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                DaifugoManager.start(player.getServer(), player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
