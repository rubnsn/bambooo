package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.skill.SkillReading;

/**
 * C→S 読書キャンセル (Esc等で画面を閉じた)。
 */
public class SkillReadCancelPacket {

    public static void encode(SkillReadCancelPacket msg, FriendlyByteBuf buf) {
    }

    public static SkillReadCancelPacket decode(FriendlyByteBuf buf) {
        return new SkillReadCancelPacket();
    }

    public static void handle(SkillReadCancelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) {
                SkillReading.onClientCancel(sp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
