package ruby.bamboo.capsule;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.entity.ai.CapsuleFollowOwnerGoal;
import ruby.bamboo.entity.ai.CapsuleOwnerHurtTargetGoal;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.CapsuleStatePacket;

/**
 * カプセルボールの判定・NBT・召喚/格納・AI付与の集約 (docs/port-spec-capsule-ball.md §3-§6)。
 */
public final class CapsuleRules {

    private CapsuleRules() {
    }

    // ===== ボール側NBT =====

    public static final String TAG_TIER = "Tier";
    public static final String TAG_CAPTURED = "Captured";
    /** 紐付け個体のUUID。「そのEntity専用」の実体 */
    public static final String TAG_BOUND = "BoundId";
    public static final String TAG_OWNER = "Owner";
    public static final String TAG_ENTITY_ID = "EntityId";
    /** 個体の saveWithoutId 全文 (+id)。Health を含む */
    public static final String TAG_ENTITY = "EntityData";
    public static final String TAG_MAX_HP = "MaxHealth";
    public static final String TAG_HEAL_TIMER = "HealTimer";

    // ===== 解放個体側 persistent =====

    /** 召喚中の目印 (再ログイン後のAI再適用・HPバー・存在チェック用) */
    public static final String FLAG_RELEASED = "CapsuleReleased";
    public static final String FLAG_OWNER = "CapsuleOwner";

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
        // 村人・行商人・アレイは不可 (ユーザー決定 §9-2)
        if (target instanceof Villager) return Category.UNCAPTURABLE;
        if (target instanceof WanderingTrader) return Category.UNCAPTURABLE;
        if (target instanceof Allay) return Category.UNCAPTURABLE;
        // 既にカプセル紐付け済みの個体は再捕獲不可
        if (isReleased(target)) return Category.UNCAPTURABLE;
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

    // ===== NBT =====

    /** 捕獲した個体タグからボールへ書き込む。 */
    public static void fillBallFromTag(ItemStack ball, CompoundTagWrapper tag, CapsuleTier tier, UUID owner) {
        var nbt = ball.getOrCreateTag();
        nbt.putString(TAG_TIER, tier.id);
        nbt.putBoolean(TAG_CAPTURED, true);
        nbt.putUUID(TAG_BOUND, tag.getUUID());
        nbt.putUUID(TAG_OWNER, owner);
        nbt.putString(TAG_ENTITY_ID, tag.getEntityId());
        nbt.put(TAG_ENTITY, tag.getData().copy());
        nbt.putDouble(TAG_MAX_HP, tag.getMaxHp());
        nbt.putInt(TAG_HEAL_TIMER, 0);
    }

    public static boolean isCaptured(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrCreateTag().getBoolean(TAG_CAPTURED);
    }

    /** 紐付け済みだが格納されていない (召喚中) 状態 */
    public static boolean isBoundEmpty(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var tag = stack.getOrCreateTag();
        return !tag.getBoolean(TAG_CAPTURED) && tag.hasUUID(TAG_BOUND);
    }

    @Nullable
    public static UUID getBoundId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var tag = stack.getOrCreateTag();
        return tag.hasUUID(TAG_BOUND) ? tag.getUUID(TAG_BOUND) : null;
    }

    @Nullable
    public static UUID getOwnerId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var tag = stack.getOrCreateTag();
        return tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
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

    // ===== 召喚・格納 =====

    /**
     * 捕獲済みボールから個体を再生成する。UUIDは BoundId に強制して同一性を維持。
     * 成功時は追跡プレイヤーへ released=true を配信する。
     */
    @Nullable
    public static Entity spawnReleased(ServerLevel level, ItemStack ball, Vec3 pos, float yaw) {
        if (!isCaptured(ball)) return null;
        var tag = ball.getOrCreateTag();
        if (!tag.contains(TAG_ENTITY_ID) || !tag.contains(TAG_ENTITY)) return null;
        var opt = EntityType.byString(tag.getString(TAG_ENTITY_ID));
        if (opt.isEmpty()) return null;
        Entity entity = opt.get().create(level);
        if (entity == null) return null;
        var data = tag.getCompound(TAG_ENTITY).copy();
        // 保存時の座標・速度は捨て、召喚位置を使う (Entity.load は Pos を読むため)
        data.remove("Pos");
        data.remove("Motion");
        data.remove("Rotation");
        entity.load(data);
        if (tag.hasUUID(TAG_BOUND)) entity.setUUID(tag.getUUID(TAG_BOUND));
        entity.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        UUID owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        entity.getPersistentData().putBoolean(FLAG_RELEASED, true);
        if (owner != null) entity.getPersistentData().putUUID(FLAG_OWNER, owner);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            if (owner != null) applyAi(mob, owner);
        }
        if (!level.addFreshEntity(entity)) return null;
        broadcastState(entity, true, (float) tag.getDouble(TAG_MAX_HP));
        return entity;
    }

    /** 紐付け空ボールへ現在の個体状態を格納する。BoundId 一致が前提。 */
    public static boolean storeInto(LivingEntity target, ItemStack boundEmpty) {
        UUID bound = getBoundId(boundEmpty);
        if (bound == null || !bound.equals(target.getUUID())) return false;
        var data = new net.minecraft.nbt.CompoundTag();
        target.saveWithoutId(data);
        data.putString("id", EntityType.getKey(target.getType()).toString());
        var tag = boundEmpty.getOrCreateTag();
        tag.putBoolean(TAG_CAPTURED, true);
        tag.put(TAG_ENTITY, data);
        tag.putDouble(TAG_MAX_HP, target.getMaxHealth());
        tag.putInt(TAG_HEAL_TIMER, 0);
        return true;
    }

    /** 解放状態の配信 (召喚=true / 格納・死亡=false)。 */
    public static void broadcastState(Entity entity, boolean released, float maxHp) {
        if (entity.level().isClientSide) return;
        BambooNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                new CapsuleStatePacket(entity.getId(), released, maxHp));
    }

    // ===== AI =====

    public static boolean isReleased(Entity entity) {
        return entity.getPersistentData().getBoolean(FLAG_RELEASED);
    }

    @Nullable
    public static UUID getReleasedOwner(Entity entity) {
        var tag = entity.getPersistentData();
        return tag.hasUUID(FLAG_OWNER) ? tag.getUUID(FLAG_OWNER) : null;
    }

    @Nullable
    public static Player getOwnerPlayer(Level level, Entity entity) {
        UUID owner = getReleasedOwner(entity);
        if (owner == null) return null;
        return level.getPlayerByUUID(owner);
    }

    /**
     * 使役AIの適用 (docs §5)。既存 targetSelector は全除去し、
     * 対Monster敵対 + 所有者被撃反撃のみに絞る。移動系は維持 + 追従を追加。
     */
    public static void applyAi(Mob mob, UUID owner) {
        mob.getPersistentData().putUUID(FLAG_OWNER, owner);
        mob.setPersistenceRequired();
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
        mob.targetSelector.addGoal(3, new CapsuleOwnerHurtTargetGoal(mob));
        // Follow は重複登録防止 (再ログイン時の再適用で増殖しないよう)
        mob.goalSelector.removeAllGoals(
                goal -> goal instanceof CapsuleFollowOwnerGoal follow && follow.isFor(mob));
        mob.goalSelector.addGoal(6, new CapsuleFollowOwnerGoal(mob));
    }

    // ===== 所有者のボール走査 =====

    /** プレイヤーが BoundId 一致のボールを持っているか (紐付け空・捕獲済み問わず)。 */
    public static boolean hasBoundBall(Player player, UUID boundId) {
        for (ItemStack stack : player.getInventory().items) {
            if (isBallOf(stack, boundId)) return true;
        }
        return isBallOf(player.getOffhandItem(), boundId);
    }

    private static boolean isBallOf(ItemStack stack, UUID boundId) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ruby.bamboo.item.CapsuleBallItem)) {
            return false;
        }
        UUID bound = getBoundId(stack);
        return boundId.equals(bound);
    }

    /**
     * 所有者の対応ボールを破壊する (個体死亡時 §4)。破壊できたら true。
     */
    public static boolean destroyBoundBall(ServerPlayer player, UUID boundId) {
        boolean destroyed = false;
        for (ItemStack stack : player.getInventory().items) {
            if (isBallOf(stack, boundId)) {
                stack.shrink(stack.getCount());
                destroyed = true;
            }
        }
        ItemStack off = player.getOffhandItem();
        if (isBallOf(off, boundId)) {
            off.shrink(off.getCount());
            destroyed = true;
        }
        return destroyed;
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

    public static ResourceLocation itemIdOf(CapsuleTier tier) {
        return ResourceLocation.fromNamespaceAndPath(ruby.bamboo.BambooMod.MODID, tier.registryName);
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
