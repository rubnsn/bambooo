package ruby.bamboo.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.daifugo.DaifugoManager;

/**
 * C→S 大富豪のお返し選択 (TRIBUTE中の上位席)。
 */
public class DaifugoTributePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DaifugoTributePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "daifugo_tribute"));

    public static final StreamCodec<FriendlyByteBuf, DaifugoTributePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DaifugoTributePacket::decode);

    public List<Integer> cards = new ArrayList<>();

    public DaifugoTributePacket() {
    }

    public DaifugoTributePacket(List<Integer> cards) {
        this.cards = cards;
    }

    public static void encode(DaifugoTributePacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.cards.stream().mapToInt(Integer::intValue).toArray());
    }

    public static DaifugoTributePacket decode(FriendlyByteBuf buf) {
        DaifugoTributePacket msg = new DaifugoTributePacket();
        for (int id : buf.readVarIntArray()) {
            msg.cards.add(id);
        }
        return msg;
    }

    public static void handle(DaifugoTributePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                DaifugoManager.tribute(player.getServer(), player, msg.cards);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
