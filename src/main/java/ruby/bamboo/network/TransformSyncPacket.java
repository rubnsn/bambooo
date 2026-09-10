package ruby.bamboo.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientTransformHandler;

/**
 * S→C 変身状態の同期。本人 + 追跡者へ配布する。
 */
public class TransformSyncPacket {

    private final UUID playerId;
    private final String entityId;

    public TransformSyncPacket(UUID playerId, String entityId) {
        this.playerId = playerId;
        this.entityId = entityId != null ? entityId : "";
    }

    public static void encode(TransformSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeUtf(msg.entityId, 128);
    }

    public static TransformSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String entity = buf.readUtf(128);
        return new TransformSyncPacket(id, entity);
    }

    public static void handle(TransformSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTransformHandler.handleSync(msg.playerId, msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
