package ruby.bamboo.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.AxeItem;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.BambooBlock;
import ruby.bamboo.core.config.WishConfig;
import ruby.bamboo.network.WishOpenPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 竹を斧で破壊した際の願い発動ハンドラ。
 * port-spec-wish §3.2 準拠。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class WishEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getState().getBlock() instanceof BambooBlock)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        if (!WishConfig.COMMON.enabled.get()) {
            return;
        }
        if (!(sp.getMainHandItem().getItem() instanceof AxeItem)) {
            return;
        }
        if (!WishConfig.COMMON.allowCreative.get() && sp.isCreative()) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Long last = COOLDOWN.get(sp.getUUID());
        if (last != null) {
            long diff = now - last;
            int cooldown = WishConfig.COMMON.cooldownTicks.get();
            if (diff < cooldown && diff >= 0) {
                return;
            }
            // handle wraparound unlikely
            if (diff < 0 && now < cooldown) {
                return;
            }
        }
        int chance = WishConfig.COMMON.chance.get();
        if (chance <= 0) {
            chance = 1;
        }
        if (sp.getRandom().nextInt(chance) != 0) {
            return;
        }
        COOLDOWN.put(sp.getUUID(), now);
        PENDING.put(sp.getUUID(), now);
        LOGGER.info("Wish triggered for player {}", sp.getName().getString());
        PacketDistributor.sendToPlayer(sp, new WishOpenPacket());
    }

    public static boolean validateAndConsumePending(ServerPlayer player) {
        UUID id = player.getUUID();
        Long issued = PENDING.get(id);
        if (issued == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        int timeout = WishConfig.COMMON.wishTimeoutTicks.get();
        if (now - issued > timeout) {
            PENDING.remove(id);
            LOGGER.warn("Wish request timeout for {}", player.getName().getString());
            return false;
        }
        PENDING.remove(id);
        return true;
    }

    /**
     * デバッグ用 WishWand からの直接発動。cooldown/chance をバイパスする。
     */
    public static void triggerForWand(ServerPlayer sp) {
        if (!WishConfig.COMMON.enabled.get()) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        PENDING.put(sp.getUUID(), now);
        LOGGER.info("Wish wand triggered for player {}", sp.getName().getString());
        PacketDistributor.sendToPlayer(sp, new WishOpenPacket());
    }

    // for testing / debugging
    public static void clearPending(UUID id) {
        PENDING.remove(id);
    }
}
