package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.daifugo.DaifugoManager;

/**
 * C→S 大富豪のローカルルール設定 (部屋主が開始前に送信)。
 */
public class DaifugoRulesPacket {
    public boolean eightCut = true;
    public boolean jback = false;
    public boolean suitLock = true;
    public boolean spe3 = true;
    public boolean miyako = true;

    public DaifugoRulesPacket() {
    }

    public DaifugoRulesPacket(boolean eightCut, boolean jback, boolean suitLock,
            boolean spe3, boolean miyako) {
        this.eightCut = eightCut;
        this.jback = jback;
        this.suitLock = suitLock;
        this.spe3 = spe3;
        this.miyako = miyako;
    }

    public static void encode(DaifugoRulesPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.eightCut);
        buf.writeBoolean(msg.jback);
        buf.writeBoolean(msg.suitLock);
        buf.writeBoolean(msg.spe3);
        buf.writeBoolean(msg.miyako);
    }

    public static DaifugoRulesPacket decode(FriendlyByteBuf buf) {
        DaifugoRulesPacket msg = new DaifugoRulesPacket();
        msg.eightCut = buf.readBoolean();
        msg.jback = buf.readBoolean();
        msg.suitLock = buf.readBoolean();
        msg.spe3 = buf.readBoolean();
        msg.miyako = buf.readBoolean();
        return msg;
    }

    public static void handle(DaifugoRulesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                DaifugoManager.rules(player.getServer(), player, msg.eightCut, msg.jback,
                        msg.suitLock, msg.spe3, msg.miyako);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
