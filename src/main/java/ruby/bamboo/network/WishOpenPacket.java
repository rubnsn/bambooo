package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.ClientWishHandler;

import java.util.function.Supplier;

/**
 * S→C 願い入力画面を開けパケット。payloadなし。
 */
public class WishOpenPacket {

    public WishOpenPacket() {
    }

    public static void encode(WishOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static WishOpenPacket decode(FriendlyByteBuf buf) {
        return new WishOpenPacket();
    }

    public static void handle(WishOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWishHandler.handleOpen()));
        ctx.get().setPacketHandled(true);
    }
}
