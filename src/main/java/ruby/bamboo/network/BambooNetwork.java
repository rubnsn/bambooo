package ruby.bamboo.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * ネットワーク登録 (1.21.1 NeoForge CustomPacketPayload方式)。
 * client→server: 鈎縄入力・閃跳・願い送信 / server→client: 願い画面開け。
 */
public final class BambooNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private BambooNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
            registrar.playToServer(KaginawaInputPacket.TYPE, KaginawaInputPacket.STREAM_CODEC, KaginawaInputPacket::handle);
            registrar.playToServer(FlashJumpPacket.TYPE, FlashJumpPacket.STREAM_CODEC, FlashJumpPacket::handle);
            registrar.playToServer(WishRequestPacket.TYPE, WishRequestPacket.STREAM_CODEC, WishRequestPacket::handle);
            registrar.playToClient(WishOpenPacket.TYPE, WishOpenPacket.STREAM_CODEC, WishOpenPacket::handle);
        });
    }
}
