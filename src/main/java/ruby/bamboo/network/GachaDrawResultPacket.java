package ruby.bamboo.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.client.handler.ClientGachaHandler;
import ruby.bamboo.gacha.GachaRarity;

/**
 * S→C 10連結果。レアリティ10件 + 現物ItemStack10件。
 * 空リストの場合は残高不足等の失敗 (スタブでは通常送られない)。
 */
public class GachaDrawResultPacket {
    public final List<GachaRarity> rarities;
    public final List<ItemStack> stacks;

    public GachaDrawResultPacket(List<GachaRarity> rarities, List<ItemStack> stacks) {
        this.rarities = rarities;
        this.stacks = stacks;
    }

    public static GachaDrawResultPacket empty() {
        return new GachaDrawResultPacket(List.of(), List.of());
    }

    public static void encode(GachaDrawResultPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.rarities.size());
        for (int i = 0; i < msg.rarities.size(); i++) {
            buf.writeVarInt(msg.rarities.get(i).ordinal());
            buf.writeItem(msg.stacks.get(i));
        }
    }

    public static GachaDrawResultPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<GachaRarity> r = new ArrayList<>(n);
        List<ItemStack> s = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            r.add(GachaRarity.byOrdinal(buf.readVarInt()));
            s.add(buf.readItem());
        }
        return new GachaDrawResultPacket(r, s);
    }

    public static void handle(GachaDrawResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientGachaHandler.onResult(msg.rarities, msg.stacks)));
        ctx.get().setPacketHandled(true);
    }
}
