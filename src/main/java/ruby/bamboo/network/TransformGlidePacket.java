package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.transform.TransformHelper;
import ruby.bamboo.transform.TransformRegistry;

/**
 * クライアント→サーバ 滑空トグルパケット。
 * 空中スペースの rising edge で送信。滑空可能種のみ反転する。
 * FlashJumpPacket と同じ検出方式 (input.jumping の立ち上がり)。
 *
 * <p>1.21.1 NeoForge: CustomPacketPayload + StreamCodec 方式へ新規実装
 * (FlashJumpPacket パターン準拠)。payload なし。
 */
public class TransformGlidePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransformGlidePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "transform_glide"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, TransformGlidePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), TransformGlidePacket::decode);

    public TransformGlidePacket() {
    }

    public static void encode(TransformGlidePacket msg, FriendlyByteBuf buf) {
    }

    public static TransformGlidePacket decode(FriendlyByteBuf buf) {
        return new TransformGlidePacket();
    }

    public static void handle(TransformGlidePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
                return;
            }
            String id = TransformHelper.resolvedId(player);
            if (!TransformRegistry.GLIDE.contains(id)) {
                return;
            }
            TransformHelper.get(player).setGlideOn(!TransformHelper.get(player).isGlideOn());
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
