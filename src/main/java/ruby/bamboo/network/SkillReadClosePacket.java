package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientSkillReadHandler;

/**
 * S→C 読書終了・中断。クライアントの ReadingScreen を閉じさせる。
 *
 * <p>1.21.1 NeoForge CustomPacketPayload 方式 (旧 SimpleChannel 全廃)。
 */
public class SkillReadClosePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillReadClosePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "skill_read_close"));

    public static final StreamCodec<FriendlyByteBuf, SkillReadClosePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SkillReadClosePacket::decode);

    public SkillReadClosePacket() {
    }

    public static void encode(SkillReadClosePacket msg, FriendlyByteBuf buf) {
    }

    public static SkillReadClosePacket decode(FriendlyByteBuf buf) {
        return new SkillReadClosePacket();
    }

    public static void handle(SkillReadClosePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(ClientSkillReadHandler::close);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
