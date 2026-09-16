package ruby.bamboo.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import ruby.bamboo.core.wish.WishHelper;
import ruby.bamboo.network.WishOpenPacket;

import java.util.Map;
import java.util.Set;
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
    /** 願いの杖由来の発動。成功率ロール・カウント対象外のため区別する。 */
    private static final Set<UUID> WAND = ConcurrentHashMap.newKeySet();

    /** 満月の夜間のみ願いが叶いやすくなる (1/256)。level#getMoonPhase は 0-7 (0=満月)。 */
    public static final int FULL_MOON_CHANCE = 256;

    public static boolean isFullMoonNight(ServerLevel level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        if (level.getMoonPhase() != 0) {
            return false;
        }
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t <= 23000L;
    }

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
        if (isFullMoonNight(sp.serverLevel())) {
            chance = FULL_MOON_CHANCE;
        }
        if (chance <= 0) {
            chance = 1;
        }
        if (sp.getRandom().nextInt(chance) != 0) {
            return;
        }
        // カウント抽選: 成功率は 100/(カウント+1)。失敗時は入力画面を開かず不発演出のみ (カウント不変)
        int wishCount = WishHelper.getCount(sp);
        if (wishCount > 0 && sp.getRandom().nextInt(wishCount + 1) != 0) {
            COOLDOWN.put(sp.getUUID(), now);
            sp.displayClientMessage(Component.translatable("bamboomod.wish.fail.luck").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            sp.displayClientMessage(Component.translatable("bamboomod.wish.fail.miss").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            LOGGER.info("Wish fizzled for {} (count {})", sp.getName().getString(), wishCount);
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
     * 成功率ロール・カウント対象外。
     */
    public static void triggerForWand(ServerPlayer sp) {
        if (!WishConfig.COMMON.enabled.get()) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        PENDING.put(sp.getUUID(), now);
        WAND.add(sp.getUUID());
        LOGGER.info("Wish wand triggered for player {}", sp.getName().getString());
        PacketDistributor.sendToPlayer(sp, new WishOpenPacket());
    }

    /** 杖由来の発動なら true を返し、フラグを消費する。 */
    public static boolean pollWand(UUID id) {
        return WAND.remove(id);
    }

    // for testing / debugging
    public static void clearPending(UUID id) {
        PENDING.remove(id);
    }
}
