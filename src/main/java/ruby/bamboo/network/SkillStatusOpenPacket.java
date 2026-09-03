package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientSkillHandler;

/**
 * S→C ステータス本を開かせる (開封前に同期済みのため最新表示)。
 */
public class SkillStatusOpenPacket {

    public static void encode(SkillStatusOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static SkillStatusOpenPacket decode(FriendlyByteBuf buf) {
        return new SkillStatusOpenPacket();
    }

    public static void handle(SkillStatusOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientSkillHandler::openStatus));
        ctx.get().setPacketHandled(true);
    }
}
