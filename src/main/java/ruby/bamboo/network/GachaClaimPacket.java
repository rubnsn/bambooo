package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.gacha.GachaManager;

/**
 * C→S 開封完了→払い出し要求。Open画面の「受取る」ボタンから送信。
 * サーバーは pending を払い出して削除する (2重受取防止)。
 */
public class GachaClaimPacket {
    public GachaClaimPacket() {
    }

    public static void encode(GachaClaimPacket msg, FriendlyByteBuf buf) {
    }

    public static GachaClaimPacket decode(FriendlyByteBuf buf) {
        return new GachaClaimPacket();
    }

    public static void handle(GachaClaimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                GachaManager.claim(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
