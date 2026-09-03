package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientSkillReadHandler;

/**
 * S→C 読書開始。クライアントに ReadingScreen を開かせる。
 */
public class SkillReadOpenPacket {

    private final String skillId;

    public SkillReadOpenPacket(String skillId) {
        this.skillId = skillId;
    }

    public static void encode(SkillReadOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.skillId);
    }

    public static SkillReadOpenPacket decode(FriendlyByteBuf buf) {
        return new SkillReadOpenPacket(buf.readUtf());
    }

    public static void handle(SkillReadOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSkillReadHandler.open(msg.skillId)));
        ctx.get().setPacketHandled(true);
    }
}
