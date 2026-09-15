package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.daifugo.DaifugoManager;

/** C→S 大富豪の退出 (ロビー=席削除、対戦中=CPU化)。 */
public class DaifugoLeavePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DaifugoLeavePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "daifugo_leave"));

    public static final StreamCodec<FriendlyByteBuf, DaifugoLeavePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DaifugoLeavePacket::decode);

    public static void encode(DaifugoLeavePacket msg, FriendlyByteBuf buf) {
    }

    public static DaifugoLeavePacket decode(FriendlyByteBuf buf) {
        return new DaifugoLeavePacket();
    }

    public static void handle(DaifugoLeavePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                DaifugoManager.leave(player.getServer(), player);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
