package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientSkillReadHandler;

/**
 * S→C 読書開始。クライアントに ReadingScreen を開かせる。
 *
 * <p>1.21.1 NeoForge CustomPacketPayload 方式 (旧 SimpleChannel 全廃)。
 */
public class SkillReadOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillReadOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "skill_read_open"));

    public static final StreamCodec<FriendlyByteBuf, SkillReadOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SkillReadOpenPacket::decode);

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

    public static void handle(SkillReadOpenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSkillReadHandler.open(msg.skillId));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
