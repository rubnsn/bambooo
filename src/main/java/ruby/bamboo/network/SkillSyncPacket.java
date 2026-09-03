package ruby.bamboo.network;

import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientSkillHandler;

/**
 * S→C スキル全量同期 (feat-spec-skill §1)。
 * ログイン・次元移動・リスポーン・変化時に送信。
 */
public class SkillSyncPacket {

    private final CompoundTag tag;

    public SkillSyncPacket(CompoundTag tag) {
        this.tag = tag.copy();
    }

    public CompoundTag getTag() {
        return tag;
    }

    public static void encode(SkillSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SkillSyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new SkillSyncPacket(tag != null ? tag : new CompoundTag());
    }

    public static void handle(SkillSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSkillHandler.handleSync(msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
