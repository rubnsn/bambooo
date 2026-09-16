package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientBlackjackHandler;

/**
 * S→C ベット残高の同期。paid>=0 は換金確定 (paid個・端数remainder点切捨て)。
 * error が空でなければ langキーの通知文。
 */
public class BlackjackBalancePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackjackBalancePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("bamboomod", "blackjack_balance"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackBalancePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), BlackjackBalancePacket::decode);

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

    public static void handle(BlackjackBalancePacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ。
        ctx.enqueueWork(() -> ClientBlackjackHandler.balance(msg));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
