package ruby.bamboo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ruby.bamboo.entity.KaginawaHookEntity;

/**
 * クライアント→サーバ 鈎縄入力パケット。
 * 毎tickフック中に送信される。reelDir: 1=伸長(Shift), -1=巻取り(Space), 0=なし
 * pull: Space+W 牽引、strafeForward/Strafe: WASD、sprint: Ctrl
 */
public class KaginawaInputPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KaginawaInputPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bamboomod", "kaginawa_input"));

    /** 既存 encode/decode をそのまま使う codec (シリアライズ内容は 1.20.1 と同一)。 */
    public static final StreamCodec<FriendlyByteBuf, KaginawaInputPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> encode(msg, buf), KaginawaInputPacket::decode);

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

    public static void handle(KaginawaInputPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
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
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
