package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.gacha.GachaManager;

/**
 * C→S 10連抽選の要求。Top画面のボタン・Spin画面の再抽選から送信。
 */
public class GachaDrawRequestPacket {
    public GachaDrawRequestPacket() {
    }

    public static void encode(GachaDrawRequestPacket msg, FriendlyByteBuf buf) {
    }

    public static GachaDrawRequestPacket decode(FriendlyByteBuf buf) {
        return new GachaDrawRequestPacket();
    }

    public static void handle(GachaDrawRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                GachaManager.request(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
