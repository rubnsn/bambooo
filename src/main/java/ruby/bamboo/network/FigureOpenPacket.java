package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientFigureHandler;

/**
 * S→C フィギュア調整GUIの開放指示 (entityIdのみ。中身はクライアント側EntityDataから読む)。
 */
public class FigureOpenPacket {

    private final int entityId;

    public FigureOpenPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(FigureOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static FigureOpenPacket decode(FriendlyByteBuf buf) {
        return new FigureOpenPacket(buf.readInt());
    }

    public static void handle(FigureOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientFigureHandler.openFigure(msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
