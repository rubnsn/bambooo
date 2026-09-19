package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.entity.FigureEntity;
import ruby.bamboo.item.MonsterFigureItem;

/**
 * C→S フィギュア調整の確定 (スケール・角度・ポーズ)。サーバー側で範囲検証して反映する。
 */
public class FigurePosePacket {

    private final int entityId;
    private final float scale;
    private final float yaw;
    private final CompoundTag pose;

    public FigurePosePacket(int entityId, float scale, float yaw, CompoundTag pose) {
        this.entityId = entityId;
        this.scale = scale;
        this.yaw = yaw;
        this.pose = pose.copy();
    }

    public static void encode(FigurePosePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeFloat(msg.scale);
        buf.writeFloat(msg.yaw);
        buf.writeNbt(msg.pose);
    }

    public static FigurePosePacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        float scale = buf.readFloat();
        float yaw = buf.readFloat();
        CompoundTag pose = buf.readNbt();
        return new FigurePosePacket(id, scale, yaw, pose != null ? pose : new CompoundTag());
    }

    public static void handle(FigurePosePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(msg.entityId);
            if (!(entity instanceof FigureEntity figure) || figure.isRemoved()) return;
            // 距離検証 (離れた個体の改変は弾く)
            if (figure.distanceTo(player) > 16.0D) return;
            String id = figure.getFigureId();
            float min = MonsterFigureItem.minScale(id);
            // NaN/無限・範囲外は弾く (Math.max/min は NaN を素通しするため個別に処理)
            float scale = msg.scale;
            if (!Float.isFinite(scale)) scale = MonsterFigureItem.defaultScale(id);
            scale = Math.max(min, Math.min(1.0F, scale));
            float yaw = msg.yaw;
            if (!Float.isFinite(yaw)) yaw = 0.0F;
            yaw = ((yaw % 360.0F) + 360.0F) % 360.0F;
            figure.setFigureScale(scale);
            figure.setFigureYaw(yaw);
            figure.setFigurePose(msg.pose.copy());
            ruby.bamboo.BambooMod.LOGGER.info(
                    "[FigurePose] applied id={} scale={} yaw={} poseKeys={}",
                    msg.entityId, scale, yaw, msg.pose.getAllKeys());
        });
        ctx.get().setPacketHandled(true);
    }
}
