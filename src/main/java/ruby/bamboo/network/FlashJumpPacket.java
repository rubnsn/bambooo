package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.enchant.FlashJumpHandler;

import java.util.function.Supplier;

/**
 * クライアント→サーバ 閃跳発動パケット。
 * クライアントでジャンプキーの rising edge を検出し、WASD入力の forward/strafe を添えて送信。
 */
public class FlashJumpPacket {

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

    public static void handle(FlashJumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            FlashJumpHandler.handlePacket(player, msg.forward, msg.strafe);
        });
        ctx.get().setPacketHandled(true);
    }
}
