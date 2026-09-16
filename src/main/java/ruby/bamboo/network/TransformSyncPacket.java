package ruby.bamboo.network;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientTransformHandler;

/**
 * S→C 変身状態の同期。本人 + 追跡者へ配布する。
 *
 * <p>1.21.1 NeoForge: CustomPacketPayload + StreamCodec 方式へ新規実装
 * (WishOpenPacket パターン準拠)。シリアライズ内容は 1.20.1 と同一 (UUID + String)。
 */
public class TransformSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransformSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "transform_sync"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, TransformSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), TransformSyncPacket::decode);

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

    public static void handle(TransformSyncPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientTransformHandler.handleSync(msg.playerId, msg.entityId));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
