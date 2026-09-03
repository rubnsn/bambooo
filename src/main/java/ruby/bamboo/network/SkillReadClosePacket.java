package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientSkillReadHandler;

/**
 * S→C 読書終了・中断。クライアントの ReadingScreen を閉じさせる。
 */
public class SkillReadClosePacket {

    public static void encode(SkillReadClosePacket msg, FriendlyByteBuf buf) {
    }

    public static SkillReadClosePacket decode(FriendlyByteBuf buf) {
        return new SkillReadClosePacket();
    }

    public static void handle(SkillReadClosePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientSkillReadHandler::close));
        ctx.get().setPacketHandled(true);
    }
}
