package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S 終了 (点→エメラルド換金)。
 */
public class BlackjackCashoutPacket {

    public static void encode(BlackjackCashoutPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackCashoutPacket decode(FriendlyByteBuf buf) {
        return new BlackjackCashoutPacket();
    }

    public static void handle(BlackjackCashoutPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                BlackjackManager.cashout(player.getServer(), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
