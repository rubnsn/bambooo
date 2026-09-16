package ruby.bamboo.transform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.network.TransformSyncPacket;

/**
 * 変身 Attachment の単一入口 (サーバー側)。
 *
 * <p>1.21.1 NeoForge: 旧 LazyOptional/Capability を捨て、Player Attachment
 * ({@link BambooCapabilities#TRANSFORM}) へ直接アクセスする。
 * 属性修正子は UUID 方式が廃止されたため ResourceLocation ID 方式へ移行。
 */
public final class TransformHelper {

    private static final ResourceLocation MOD_SPEED =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "transform_speed");
    private static final ResourceLocation MOD_HEALTH =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "transform_health");
    private static final ResourceLocation MOD_ATTACK =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "transform_attack");
    private static final ResourceLocation MOD_KNOCKBACK =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "transform_knockback");

    /** 属性適用済み管理 (差分時のみ付け外しして同期パケット乱発を防ぐ)。 */
    private static final Map<UUID, String> APPLIED = new ConcurrentHashMap<>();

    private TransformHelper() {
    }

    public static TransformStorage get(Player player) {
        return player.getData(BambooCapabilities.TRANSFORM);
    }

    public static String getId(Player player) {
        TransformStorage s = get(player);
        return s != null ? s.getEntityId() : "";
    }

    public static boolean isHuman(Player player) {
        return getId(player).isEmpty();
    }

    /** 保存 id を引き直し、不正なら人間扱いにする。 */
    public static String resolvedId(Player player) {
        String id = getId(player);
        if (id.isEmpty()) {
            return "";
        }
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return id;
        }
        return TransformRegistry.isLivingId(id, level) ? id : "";
    }

    public static void setTransform(ServerPlayer player, String entityId) {
        get(player).setEntityId(entityId);
        applyAttributes(player);
        sync(player);
    }

    public static void clearTransform(ServerPlayer player) {
        get(player).clear();
        applyAttributes(player);
        sync(player);
    }

    /** ログイン/リスポーン時の再解決。不正 id は人間へフォールバックする。 */
    public static void revalidate(ServerPlayer player) {
        String raw = getId(player);
        if (!raw.isEmpty() && resolvedId(player).isEmpty()) {
            get(player).clear();
        }
        applyAttributes(player);
        sync(player);
    }

    /** 差分がある場合のみ属性を付け替える。 */
    public static void applyAttributes(Player player) {
        String id = resolvedId(player);
        String prev = APPLIED.get(player.getUUID());
        if (id.equals(prev)) {
            return;
        }
        removeMods(player);
        if (!id.isEmpty()) {
            double speed = TransformRegistry.SPEED.getOrDefault(id, 1.0D);
            if (speed != 1.0D) {
                mod(player, Attributes.MOVEMENT_SPEED, MOD_SPEED, speed - 1.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            double health = TransformRegistry.HEALTH.getOrDefault(id, 0.0D);
            if (health != 0.0D) {
                mod(player, Attributes.MAX_HEALTH, MOD_HEALTH, health,
                        AttributeModifier.Operation.ADD_VALUE);
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
            double attack = TransformRegistry.ATTACK.getOrDefault(id, 0.0D);
            if (attack != 0.0D) {
                mod(player, Attributes.ATTACK_DAMAGE, MOD_ATTACK, attack,
                        AttributeModifier.Operation.ADD_VALUE);
            }
            double kb = TransformRegistry.KNOCKBACK.getOrDefault(id, 0.0D);
            if (kb != 0.0D) {
                mod(player, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, kb,
                        AttributeModifier.Operation.ADD_VALUE);
            }
        }
        if (id.isEmpty()) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), id);
        }
    }

    private static void mod(Player player, Holder<Attribute> attr,
            ResourceLocation mid, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(mid);
        inst.addTransientModifier(new AttributeModifier(mid, amount, op));
    }

    private static void removeMods(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOD_SPEED);
        }
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(MOD_HEALTH);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(MOD_ATTACK);
        }
        AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(MOD_KNOCKBACK);
        }
    }

    public static void dropCache(UUID id) {
        APPLIED.remove(id);
    }

    /** 本人+追跡者へ配布する (他人から見た変身に必要)。 */
    public static void sync(ServerPlayer player) {
        String id = resolvedId(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TransformSyncPacket(player.getUUID(), id));
    }
}
