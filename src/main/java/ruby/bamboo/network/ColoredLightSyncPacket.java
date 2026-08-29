package ruby.bamboo.network;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.core.init.BambooCapabilities;

import java.util.function.Supplier;

/**
 * Phase B: チャンク内 ColoredLight sparse同期パケット (server -> client).
 * ChunkPos + Map&lt;Long,int(p),int(c)&gt; を運ぶ。
 */
public class ColoredLightSyncPacket {

    private final ChunkPos pos;
    private final Object2IntMap<Long> lights;

    public ColoredLightSyncPacket(ChunkPos pos, Object2IntMap<Long> lights) {
        this.pos = pos;
        // 防御コピー
        this.lights = new Object2IntOpenHashMap<>(lights);
    }

    public ChunkPos getPos() {
        return pos;
    }

    public Object2IntMap<Long> getLights() {
        return lights;
    }

    public static void encode(ColoredLightSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.pos.x);
        buf.writeInt(msg.pos.z);
        buf.writeVarInt(msg.lights.size());
        for (Object2IntMap.Entry<Long> e : msg.lights.object2IntEntrySet()) {
            buf.writeLong(e.getKey().longValue());
            buf.writeInt(e.getIntValue());
        }
    }

    public static ColoredLightSyncPacket decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int z = buf.readInt();
        int size = buf.readVarInt();
        ChunkPos pos = new ChunkPos(x, z);
        Object2IntMap<Long> map = new Object2IntOpenHashMap<>(size);
        for (int i = 0; i < size; i++) {
            long p = buf.readLong();
            int c = buf.readInt();
            map.put(Long.valueOf(p), c);
        }
        return new ColoredLightSyncPacket(pos, map);
    }

    public static void handle(ColoredLightSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // クライアント側のみで処理
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClient(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(ColoredLightSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!mc.level.hasChunk(msg.pos.x, msg.pos.z)) {
            return;
        }
        LevelChunk chunk = mc.level.getChunk(msg.pos.x, msg.pos.z);
        chunk.getCapability(BambooCapabilities.COLORED_LIGHT).ifPresent(storage -> {
            storage.getMap().clear();
            storage.getMap().putAll(msg.lights);
        });
    }
}
