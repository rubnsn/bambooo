package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.handler.FishingHandler;

import java.util.function.Supplier;

/**
 * C→S 釣り結果報告。success=0, fail=1, cancel=2。
 */
public class FishingResultPacket {

    public static final int SUCCESS = 0;
    public static final int FAIL = 1;
    public static final int CANCEL = 2;

    private final int resultType;

    public FishingResultPacket(int resultType) {
        this.resultType = resultType;
    }

    public int getResultType() {
        return resultType;
    }

    public static void encode(FishingResultPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.resultType);
    }

    public static FishingResultPacket decode(FriendlyByteBuf buf) {
        int t = buf.readByte();
        return new FishingResultPacket(t);
    }

    public static void handle(FishingResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.resultType < 0 || msg.resultType > 2) {
                BambooMod.LOGGER.warn("Invalid fishing result type {}", msg.resultType);
                return;
            }
            FishingHandler.handleResult(player, msg.resultType);
        });
        ctx.get().setPacketHandled(true);
    }
}
