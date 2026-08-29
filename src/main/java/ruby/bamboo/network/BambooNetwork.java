package ruby.bamboo.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ruby.bamboo.BambooMod;

/**
 * ネットワークチャンネル (1.20.1 Forge 47.4.10)。
 * 鈎縄の入力同期に使用。
 */
public final class BambooNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(BambooMod.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(s -> true)
            .serverAcceptedVersions(s -> true)
            .simpleChannel();

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    private BambooNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId(), KaginawaInputPacket.class,
                KaginawaInputPacket::encode,
                KaginawaInputPacket::decode,
                KaginawaInputPacket::handle);
        CHANNEL.registerMessage(nextId(), FlashJumpPacket.class,
                FlashJumpPacket::encode,
                FlashJumpPacket::decode,
                FlashJumpPacket::handle);
    }
}
