package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.enchant.FlashJumpHandler;

/**
 * クライアント→サーバ 閃跳発動パケット。
 * クライアントでジャンプキーの rising edge を検出し、WASD入力の forward/strafe を添えて送信。
 */
public class FlashJumpPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FlashJumpPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "flash_jump"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, FlashJumpPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), FlashJumpPacket::decode);

    private final float forward;
    private final float strafe;

    public FlashJumpPacket(float forward, float strafe) {
        this.forward = forward;
        this.strafe = strafe;
    }

    public static void encode(FlashJumpPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.forward);
        buf.writeFloat(msg.strafe);
    }

    public static FlashJumpPacket decode(FriendlyByteBuf buf) {
        float fwd = buf.readFloat();
        float str = buf.readFloat();
        return new FlashJumpPacket(fwd, str);
    }

    public static void handle(FlashJumpPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            FlashJumpHandler.handlePacket(player, msg.forward, msg.strafe);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
