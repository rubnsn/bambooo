package ruby.bamboo.transform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.TransformSyncPacket;

/**
 * 変身 Cap の単一入口 (サーバー側)。
 */
public final class TransformHelper {

    private static final UUID MOD_SPEED = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0001");
    private static final UUID MOD_HEALTH = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0002");
    private static final UUID MOD_ATTACK = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0003");
    private static final UUID MOD_KNOCKBACK = UUID.fromString("c3b8c7a1-1b2e-4d5f-9a1b-2c3d4e5f0004");

    /** 属性適用済み管理 (差分時のみ付け外しして同期パケット乱発を防ぐ)。 */
    private static final Map<UUID, String> APPLIED = new ConcurrentHashMap<>();

    private TransformHelper() {
    }

    public static LazyOptional<TransformStorage> get(Player player) {
        return player.getCapability(BambooCapabilities.TRANSFORM);
    }

    public static String getId(Player player) {
        return get(player).map(TransformStorage::getEntityId).orElse("");
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
        get(player).ifPresent(s -> s.setEntityId(entityId));
        applyAttributes(player);
        sync(player);
    }

    public static void clearTransform(ServerPlayer player) {
        get(player).ifPresent(TransformStorage::clear);
        applyAttributes(player);
        sync(player);
    }

    /** ログイン/リスポーン時の再解決。不正 id は人間へフォールバックする。 */
    public static void revalidate(ServerPlayer player) {
        String raw = getId(player);
        if (!raw.isEmpty() && resolvedId(player).isEmpty()) {
            get(player).ifPresent(TransformStorage::clear);
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
                mod(player, Attributes.MOVEMENT_SPEED, MOD_SPEED, "transform_speed", speed - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL);
            }
            double health = TransformRegistry.HEALTH.getOrDefault(id, 0.0D);
            if (health != 0.0D) {
                mod(player, Attributes.MAX_HEALTH, MOD_HEALTH, "transform_health", health,
                        AttributeModifier.Operation.ADDITION);
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
            double attack = TransformRegistry.ATTACK.getOrDefault(id, 0.0D);
            if (attack != 0.0D) {
                mod(player, Attributes.ATTACK_DAMAGE, MOD_ATTACK, "transform_attack", attack,
                        AttributeModifier.Operation.ADDITION);
            }
            double kb = TransformRegistry.KNOCKBACK.getOrDefault(id, 0.0D);
            if (kb != 0.0D) {
                mod(player, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, "transform_kb", kb,
                        AttributeModifier.Operation.ADDITION);
            }
        }
        if (id.isEmpty()) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), id);
        }
    }

    private static void mod(Player player, net.minecraft.world.entity.ai.attributes.Attribute attr, UUID uuid,
            String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(uuid);
        inst.addTransientModifier(new AttributeModifier(uuid, name, amount, op));
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
        BambooNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new TransformSyncPacket(player.getUUID(), id));
    }
}
