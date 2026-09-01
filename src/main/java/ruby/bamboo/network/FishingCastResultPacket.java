package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientFishingHandler;

import java.util.function.Supplier;

/**
 * S→C 釣果通知。キャスト時にサーバーが抽選した釣果をクライアントへ送信し、待ち・ミニゲームを開始させる。
 */
public class FishingCastResultPacket {

    public final ResourceLocation entryId;
    public final ResourceLocation itemId;
    public final int categoryOrdinal;
    public final int sizeOrdinal;
    public final int startProgress;
    public final int fishStamina;
    public final int fishPower;
    public final int movePatternOrdinal;
    public final int distance;
    public final int waitMin;
    public final int waitMax;
    public final int minCm;
    public final int midCm;
    public final int maxCm;

    public FishingCastResultPacket(ResourceLocation entryId, ResourceLocation itemId,
                                   int categoryOrdinal, int sizeOrdinal,
                                   int startProgress, int fishStamina, int fishPower,
                                   int movePatternOrdinal, int distance,
                                   int waitMin, int waitMax,
                                   int minCm, int midCm, int maxCm) {
        this.entryId = entryId;
        this.itemId = itemId;
        this.categoryOrdinal = categoryOrdinal;
        this.sizeOrdinal = sizeOrdinal;
        this.startProgress = startProgress;
        this.fishStamina = fishStamina;
        this.fishPower = fishPower;
        this.movePatternOrdinal = movePatternOrdinal;
        this.distance = distance;
        this.waitMin = waitMin;
        this.waitMax = waitMax;
        this.minCm = minCm;
        this.midCm = midCm;
        this.maxCm = maxCm;
    }

    public static void encode(FishingCastResultPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.entryId);
        buf.writeResourceLocation(msg.itemId);
        buf.writeByte(msg.categoryOrdinal);
        buf.writeByte(msg.sizeOrdinal);
        buf.writeVarInt(msg.startProgress);
        buf.writeVarInt(msg.fishStamina);
        buf.writeVarInt(msg.fishPower);
        buf.writeByte(msg.movePatternOrdinal);
        buf.writeVarInt(msg.distance);
        buf.writeVarInt(msg.waitMin);
        buf.writeVarInt(msg.waitMax);
        buf.writeVarInt(msg.minCm);
        buf.writeVarInt(msg.midCm);
        buf.writeVarInt(msg.maxCm);
    }

    public static FishingCastResultPacket decode(FriendlyByteBuf buf) {
        ResourceLocation entryId = buf.readResourceLocation();
        ResourceLocation itemId = buf.readResourceLocation();
        int cat = buf.readByte();
        int size = buf.readByte();
        int startP = buf.readVarInt();
        int stamina = buf.readVarInt();
        int power = buf.readVarInt();
        int move = buf.readByte();
        int dist = buf.readVarInt();
        int waitMin = buf.readVarInt();
        int waitMax = buf.readVarInt();
        int minCm = buf.readVarInt();
        int midCm = buf.readVarInt();
        int maxCm = buf.readVarInt();
        return new FishingCastResultPacket(entryId, itemId, cat, size, startP, stamina, power, move, dist, waitMin, waitMax, minCm, midCm, maxCm);
    }

    public static void handle(FishingCastResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientFishingHandler.handleCastResult(msg)));
        ctx.get().setPacketHandled(true);
    }
}
