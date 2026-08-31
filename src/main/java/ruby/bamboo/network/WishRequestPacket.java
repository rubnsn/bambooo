package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.handler.WishEventHandler;

import java.util.function.Supplier;

/**
 * C→S 願い送信パケット。最大30文字。
 */
public class WishRequestPacket {

    private final String wish;

    public WishRequestPacket(String wish) {
        this.wish = wish;
    }

    public String getWish() {
        return wish;
    }

    public static void encode(WishRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.wish, 30);
    }

    public static WishRequestPacket decode(FriendlyByteBuf buf) {
        String s = buf.readUtf(30);
        return new WishRequestPacket(s);
    }

    public static void handle(WishRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (!WishEventHandler.validateAndConsumePending(player)) {
                BambooMod.LOGGER.warn("Wish request without valid pending from {}", player.getName().getString());
                return;
            }
            String raw = msg.wish;
            if (raw == null) {
                raw = "";
            }
            raw = raw.trim();
            if (raw.length() > 30) {
                raw = raw.substring(0, 30);
            }
            raw = raw.replaceAll("\\p{Cntrl}", "");
            BambooMod.LOGGER.info("Wish received from {}: '{}'", player.getName().getString(), raw);
            ruby.bamboo.core.wish.WishManager.resolveAndExecute(player, raw);
        });
        ctx.get().setPacketHandled(true);
    }
}
