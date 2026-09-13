package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S 終了せずに閉じた (没収)。
 */
public class BlackjackAbandonPacket {

    public static void encode(BlackjackAbandonPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackAbandonPacket decode(FriendlyByteBuf buf) {
        return new BlackjackAbandonPacket();
    }

    public static void handle(BlackjackAbandonPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlackjackManager.abandon(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
