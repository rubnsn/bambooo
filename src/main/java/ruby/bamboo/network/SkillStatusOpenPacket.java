package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientSkillHandler;

/**
 * S→C ステータス本を開かせる (開封前に同期済みのため最新表示)。
 *
 * <p>1.21.1 NeoForge CustomPacketPayload 方式 (旧 SimpleChannel 全廃)。
 */
public class SkillStatusOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillStatusOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "skill_status_open"));

    public static final StreamCodec<FriendlyByteBuf, SkillStatusOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SkillStatusOpenPacket::decode);

    public SkillStatusOpenPacket() {
    }

    public static void encode(SkillStatusOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static SkillStatusOpenPacket decode(FriendlyByteBuf buf) {
        return new SkillStatusOpenPacket();
    }

    public static void handle(SkillStatusOpenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(ClientSkillHandler::openStatus);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
