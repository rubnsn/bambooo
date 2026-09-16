package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.skill.SkillReading;

/**
 * C→S 読書キャンセル (Esc等で画面を閉じた)。
 *
 * <p>1.21.1 NeoForge CustomPacketPayload 方式 (旧 SimpleChannel 全廃)。
 */
public class SkillReadCancelPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillReadCancelPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "skill_read_cancel"));

    public static final StreamCodec<FriendlyByteBuf, SkillReadCancelPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), SkillReadCancelPacket::decode);

    public SkillReadCancelPacket() {
    }

    public static void encode(SkillReadCancelPacket msg, FriendlyByteBuf buf) {
    }

    public static SkillReadCancelPacket decode(FriendlyByteBuf buf) {
        return new SkillReadCancelPacket();
    }

    public static void handle(SkillReadCancelPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                SkillReading.onClientCancel(sp);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
