package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.client.handler.ClientFishingHandler;

/**
 * S→C 釣果通知。キャスト時にサーバーが抽選した釣果をクライアントへ送信し、待ち・ミニゲームを開始させる。
 *
 * <p>1.21.1 NeoForge: CustomPacketPayload + StreamCodec 形式 (WishOpenPacket の先例)。
 */
public class FishingCastResultPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FishingCastResultPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "fishing_cast_result"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, FishingCastResultPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), FishingCastResultPacket::decode);

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

    public FishingCastResultPacket(ResourceLocation entryId, ResourceLocation itemId,
                                   int categoryOrdinal, int sizeOrdinal,
                                   int startProgress, int fishStamina, int fishPower,
                                   int movePatternOrdinal, int distance,
                                   int waitMin, int waitMax) {
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
        return new FishingCastResultPacket(entryId, itemId, cat, size, startP, stamina, power, move, dist, waitMin, waitMax);
    }

    public static void handle(FishingCastResultPacket msg, IPayloadContext ctx) {
        // playToClient のためサーバでは実行されない。クライアントハンドラを直接呼ぶ (WishOpenPacket の先例)。
        ctx.enqueueWork(() -> ClientFishingHandler.handleCastResult(msg));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
