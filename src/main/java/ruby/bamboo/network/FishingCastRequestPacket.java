package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.handler.FishingHandler;

import java.util.function.Supplier;

/**
 * C→S キャストリクエスト。パワーゲージで決定した距離 (4-15) をサーバーへ送信する。
 */
public class FishingCastRequestPacket {

    private final int distance;

    public FishingCastRequestPacket(int distance) {
        this.distance = distance;
    }

    public int getDistance() {
        return distance;
    }

    public static void encode(FishingCastRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.distance);
    }

    public static FishingCastRequestPacket decode(FriendlyByteBuf buf) {
        int d = buf.readVarInt();
        return new FishingCastRequestPacket(d);
    }

    public static void handle(FishingCastRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            int d = msg.distance;
            if (d < 4) d = 4;
            if (d > 15) d = 15;
            FishingHandler.handleCastRequest(player, d);
        });
        ctx.get().setPacketHandled(true);
    }
}
