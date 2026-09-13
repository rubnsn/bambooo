package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientBlackjackHandler;

/**
 * S→C ブラックジャック画面を開かせる (テスト用)。
 */
public class BlackjackOpenPacket {

    public static void encode(BlackjackOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static BlackjackOpenPacket decode(FriendlyByteBuf buf) {
        return new BlackjackOpenPacket();
    }

    public static void handle(BlackjackOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientBlackjackHandler::open));
        ctx.get().setPacketHandled(true);
    }
}
