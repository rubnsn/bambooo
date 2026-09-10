package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.transform.TransformHelper;
import ruby.bamboo.transform.TransformRegistry;

/**
 * クライアント→サーバ 滑空トグルパケット。
 * 空中スペースの rising edge で送信。滑空可能種のみ反転する。
 * FlashJumpPacket と同じ検出方式 (input.jumping の立ち上がり)。
 */
public class TransformGlidePacket {

    public TransformGlidePacket() {
    }

    public static void encode(TransformGlidePacket msg, FriendlyByteBuf buf) {
    }

    public static TransformGlidePacket decode(FriendlyByteBuf buf) {
        return new TransformGlidePacket();
    }

    public static void handle(TransformGlidePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
                return;
            }
            String id = TransformHelper.resolvedId(player);
            if (!TransformRegistry.GLIDE.contains(id)) {
                return;
            }
            TransformHelper.get(player).ifPresent(s -> {
                s.setGlideOn(!s.isGlideOn());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
