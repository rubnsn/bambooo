package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.daifugo.DaifugoManager;

/** C→S 大富豪の開始 (オーナーのみ)。 */
public class DaifugoStartPacket {

    public static void encode(DaifugoStartPacket msg, FriendlyByteBuf buf) {
    }

    public static DaifugoStartPacket decode(FriendlyByteBuf buf) {
        return new DaifugoStartPacket();
    }

    public static void handle(DaifugoStartPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.start(player.getServer(), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
