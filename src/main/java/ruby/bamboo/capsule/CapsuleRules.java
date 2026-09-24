package ruby.bamboo.capsule;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

/**
 * カプセルボールの捕獲判定・確率・NBT採取の集約 (docs/port-spec-capsule-ball.md §3)。
 * 使役系 (§4-§6) はフィギュア方針への変更に伴い撤去済み。
 */
public final class CapsuleRules {

    private CapsuleRules() {
    }

    /** ボールの Tier を示すNBTキー */
    public static final String TAG_TIER = "Tier";

    /** 捕獲対象分類 (docs §3.1。上から優先) */
    public enum Category {
        UNCAPTURABLE,
        HOSTILE,
        NEUTRAL,
        FRIENDLY,
    }

    /** 分類ベース値 */
    public static int baseRate(Category category) {
        return switch (category) {
            case HOSTILE -> 0;
            case NEUTRAL -> 50;
            case FRIENDLY -> 50;
            default -> 0;
        };
    }

    public static Category classify(LivingEntity target, @Nullable UUID throwerId) {
        if (target instanceof Player) return Category.UNCAPTURABLE;
        if (target instanceof EnderDragon) return Category.UNCAPTURABLE;
        if (target instanceof WitherBoss) return Category.UNCAPTURABLE;
        if (target instanceof Warden) return Category.UNCAPTURABLE;
        if (target instanceof TamableAnimal tamable && tamable.isTame()) {
            // 他人のペットは不可。自分のペットは友好扱い
            if (throwerId == null || !throwerId.equals(tamable.getOwnerUUID())) {
                return Category.UNCAPTURABLE;
            }
            return Category.FRIENDLY;
        }
        if (target instanceof Enemy) return Category.HOSTILE;
        if (target instanceof NeutralMob) return Category.NEUTRAL;
        return Category.FRIENDLY;
    }

    /**
     * 1回分の捕獲成功率 (docs §3.2)。
     * p_pre + (1-p_pre)*(1-ratio)^2。HP1以下は下限90%。
     */
    public static float probability(int base, int bonus, float hp, float maxHp) {
        float pre = Math.max(0.0F, Math.min(1.0F, (base + bonus) / 100.0F));
        if (pre >= 1.0F) return 1.0F;
        float ratio = maxHp <= 0.0F ? 1.0F : Math.max(0.0F, Math.min(1.0F, hp / maxHp));
        float miss = 1.0F - ratio;
        float p = pre + (1.0F - pre) * miss * miss;
        if (hp <= 1.0F) p = Math.max(p, 0.9F);
        return Math.min(1.0F, p);
    }

    /** 生きた個体から保存タグを採取する (discard 前に呼ぶ)。 */
    public static CompoundTagWrapper captureOf(LivingEntity target) {
        var data = new net.minecraft.nbt.CompoundTag();
        target.saveWithoutId(data);
        String id = EntityType.getKey(target.getType()).toString();
        data.putString("id", id);
        float maxHp = target.getMaxHealth();
        return new CompoundTagWrapper(target.getUUID(), id, data, maxHp);
    }

    /** EntityTypeキーから表示名を取る (tooltip用。解決不可ならキー文字列)。 */
    public static String describeEntityId(@Nullable String entityId) {
        if (entityId == null) return "?";
        var opt = EntityType.byString(entityId);
        if (opt.isEmpty()) return entityId;
        try {
            return opt.get().getDescription().getString();
        } catch (Exception e) {
            return entityId;
        }
    }

    /** 採取タグの値オブジェクト (NBTラッパの受け渡し用)。 */
    public record CompoundTagWrapper(UUID uuid, String entityId, net.minecraft.nbt.CompoundTag data, float maxHp) {
        public UUID getUUID() {
            return uuid;
        }

        public String getEntityId() {
            return entityId;
        }

        public net.minecraft.nbt.CompoundTag getData() {
            return data;
        }

        public float getMaxHp() {
            return maxHp;
        }
    }
}
