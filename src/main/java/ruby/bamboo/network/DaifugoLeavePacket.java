package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.daifugo.DaifugoManager;

/** C→S 大富豪の退出 (ロビー=席削除、対戦中=CPU化)。 */
public class DaifugoLeavePacket {

    public static void encode(DaifugoLeavePacket msg, FriendlyByteBuf buf) {
    }

    public static DaifugoLeavePacket decode(FriendlyByteBuf buf) {
        return new DaifugoLeavePacket();
    }

    public static void handle(DaifugoLeavePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.leave(player.getServer(), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
