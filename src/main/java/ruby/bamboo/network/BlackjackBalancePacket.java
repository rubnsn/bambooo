package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientBlackjackHandler;

/**
 * S→C ベット残高の同期。paid>=0 は換金確定 (paid個・端数remainder点切捨て)。
 * error が空でなければ langキーの通知文。
 */
public class BlackjackBalancePacket {
    public int balance;
    public boolean active;
    public int paid = -1;
    public int remainder;
    public String error = "";

    public BlackjackBalancePacket() {
    }

    public BlackjackBalancePacket(int balance, boolean active, int paid, int remainder,
            String error) {
        this.balance = balance;
        this.active = active;
        this.paid = paid;
        this.remainder = remainder;
        this.error = error;
    }

    public static void encode(BlackjackBalancePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.balance);
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.paid);
        buf.writeVarInt(msg.remainder);
        buf.writeUtf(msg.error, 128);
    }

    public static BlackjackBalancePacket decode(FriendlyByteBuf buf) {
        return new BlackjackBalancePacket(buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf(128));
    }

    public static void handle(BlackjackBalancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientBlackjackHandler.balance(msg)));
        ctx.get().setPacketHandled(true);
    }
}
