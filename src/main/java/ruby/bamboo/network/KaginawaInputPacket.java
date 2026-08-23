package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ruby.bamboo.entity.KaginawaHookEntity;

import java.util.function.Supplier;

/**
 * クライアント→サーバ 鈎縄入力パケット。
 * 毎tickフック中に送信される。reelDir: 1=伸長(Shift), -1=巻取り(Space), 0=なし
 * pull: Space+W 牽引、strafeForward/Strafe: WASD、sprint: Ctrl
 */
public class KaginawaInputPacket {

    private final byte reelDir; // -1,0,1
    private final boolean pull;
    private final float strafeForward; // W(+1) S(-1)
    private final float strafeSide; // D(+1) A(-1)
    private final boolean sprint;

    public KaginawaInputPacket(byte reelDir, boolean pull, float strafeForward, float strafeSide, boolean sprint) {
        this.reelDir = reelDir;
        this.pull = pull;
        this.strafeForward = strafeForward;
        this.strafeSide = strafeSide;
        this.sprint = sprint;
    }

    public static void encode(KaginawaInputPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.reelDir);
        buf.writeBoolean(msg.pull);
        buf.writeFloat(msg.strafeForward);
        buf.writeFloat(msg.strafeSide);
        buf.writeBoolean(msg.sprint);
    }

    public static KaginawaInputPacket decode(FriendlyByteBuf buf) {
        byte reelDir = buf.readByte();
        boolean pull = buf.readBoolean();
        float fwd = buf.readFloat();
        float side = buf.readFloat();
        boolean sprint = buf.readBoolean();
        return new KaginawaInputPacket(reelDir, pull, fwd, side, sprint);
    }

    public static void handle(KaginawaInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            KaginawaHookEntity hook = KaginawaStateManager.getHook(player);
            if (hook == null || hook.isRemoved()) {
                return;
            }
            // owner検証 (参照比較ではなくUUIDで比較)
            var owner = hook.getOwnerPlayer();
            if (owner == null || !owner.getUUID().equals(player.getUUID())) {
                return;
            }
            hook.handleInput(msg.reelDir, msg.pull, msg.strafeForward, msg.strafeSide, msg.sprint);
        });
        ctx.get().setPacketHandled(true);
    }
}
