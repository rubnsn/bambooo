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
 * C→S 大富豪の着手 (空配列=パス)。
 * stairs は [X,JK,JK] リード時の宣言 (true=階段)。
 */
public class DaifugoActionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DaifugoActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "daifugo_action"));

    public static final StreamCodec<FriendlyByteBuf, DaifugoActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), DaifugoActionPacket::decode);

    public List<Integer> cards = new ArrayList<>();
    public boolean stairs = false;

    public DaifugoActionPacket() {
    }

    public DaifugoActionPacket(List<Integer> cards) {
        this.cards = cards;
    }

    public DaifugoActionPacket(List<Integer> cards, boolean stairs) {
        this.cards = cards;
        this.stairs = stairs;
    }

    public static void encode(DaifugoActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.cards.stream().mapToInt(Integer::intValue).toArray());
        buf.writeBoolean(msg.stairs);
    }

    public static DaifugoActionPacket decode(FriendlyByteBuf buf) {
        DaifugoActionPacket msg = new DaifugoActionPacket();
        for (int id : buf.readVarIntArray()) {
            msg.cards.add(id);
        }
        msg.stairs = buf.readBoolean();
        return msg;
    }

    public static void handle(DaifugoActionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.getServer() != null) {
                DaifugoManager.action(player.getServer(), player, msg.cards, msg.stairs);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
