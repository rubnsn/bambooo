package ruby.bamboo.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientSkillHandler;

/**
 * S→C スキル全量同期 (feat-spec-skill §1)。
 * ログイン・次元移動・リスポーン・変化時に送信。
 *
 * <p>1.21.1 NeoForge CustomPacketPayload 方式 (旧 SimpleChannel 全廃)。
 */
public class SkillSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "skill_sync"));

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SkillSyncPacket::decode);

    private final CompoundTag tag;

    public SkillSyncPacket(CompoundTag tag) {
        this.tag = tag.copy();
    }

    public CompoundTag getTag() {
        return tag;
    }

    public static void encode(SkillSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SkillSyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SkillSyncPacket(tag != null ? tag : new CompoundTag());
    }

    public static void handle(SkillSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSkillHandler.handleSync(msg.tag));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
