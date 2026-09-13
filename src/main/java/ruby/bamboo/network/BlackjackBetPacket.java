package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.blackjack.BlackjackManager;

/**
 * C→S ベットモードの入金 (エメラルド→点)。
 */
public class BlackjackBetPacket {
    public int emeralds = 1;

    public BlackjackBetPacket() {
    }

    public BlackjackBetPacket(int emeralds) {
        this.emeralds = emeralds;
    }

    public static void encode(BlackjackBetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.emeralds);
    }

    public static BlackjackBetPacket decode(FriendlyByteBuf buf) {
        return new BlackjackBetPacket(buf.readVarInt());
    }

    public static void handle(BlackjackBetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                BlackjackManager.bet(player.getServer(), player, msg.emeralds);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
