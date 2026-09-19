package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientCapsuleHandler;

/**
 * S→C カプセル解放状態の配信 (entityId単位)。召喚で released=true、
 * 格納・死亡で released=false を追跡プレイヤーへ送る。
 */
public class CapsuleStatePacket {

    private final int entityId;
    private final boolean released;
    private final float maxHp;

    public CapsuleStatePacket(int entityId, boolean released, float maxHp) {
        this.entityId = entityId;
        this.released = released;
        this.maxHp = maxHp;
    }

    public static void encode(CapsuleStatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.released);
        buf.writeFloat(msg.maxHp);
    }

    public static CapsuleStatePacket decode(FriendlyByteBuf buf) {
        return new CapsuleStatePacket(buf.readInt(), buf.readBoolean(), buf.readFloat());
    }

    public static void handle(CapsuleStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCapsuleHandler.handleState(msg.entityId, msg.released, msg.maxHp)));
        ctx.get().setPacketHandled(true);
    }
}
