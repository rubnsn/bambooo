package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientFreeCellHandler;

/**
 * S→C フリーセル画面を開かせる (テスト用)。
 */
public class FreeCellOpenPacket {

    public static void encode(FreeCellOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static FreeCellOpenPacket decode(FriendlyByteBuf buf) {
        return new FreeCellOpenPacket();
    }

    public static void handle(FreeCellOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientFreeCellHandler::open));
        ctx.get().setPacketHandled(true);
    }
}
