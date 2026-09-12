package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientSolitaireHandler;

/**
 * S→C ソリティア画面を開かせる (テスト用)。
 */
public class SolitaireOpenPacket {

    public static void encode(SolitaireOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static SolitaireOpenPacket decode(FriendlyByteBuf buf) {
        return new SolitaireOpenPacket();
    }

    public static void handle(SolitaireOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientSolitaireHandler::open));
        ctx.get().setPacketHandled(true);
    }
}
