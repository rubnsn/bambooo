package ruby.bamboo.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.capsule.CapsuleRules;
import ruby.bamboo.capsule.CapsuleTier;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.item.CapsuleBallItem;

/**
 * カプセルボールの投擲体 (docs/port-spec-capsule-ball.md §3)。
 * 捕獲モード: 命中→吸収→3揺れ抽選 (サーバー権威)。召喚モード: 着弾で再生成。
 */
public class CapsuleBallEntity extends ThrowableItemProjectile {

    public static final int MODE_CAPTURE = 0;
    public static final int MODE_SUMMON = 1;

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(
            CapsuleBallEntity.class, EntityDataSerializers.INT);
    /** 完了した揺れ回数 (0-3。クライアントの演出同期用) */
    private static final EntityDataAccessor<Integer> DATA_SHAKE = SynchedEntityData.defineId(
            CapsuleBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(
            CapsuleBallEntity.class, EntityDataSerializers.INT);

    /** 揺れ1回あたりのtick */
    private static final int SHAKE_TICKS = 20;
    private static final int SHAKE_COUNT = 3;
    /** 飛行 failsafe (虚空等): 捕獲200tick / 召喚120tick */
    private static final int FLY_TIMEOUT_CAPTURE = 200;
    private static final int FLY_TIMEOUT_SUMMON = 120;

    private int shakeTimer;
    /** 吸収中の個体タグ (id付き)。null=飛行中 */
    @Nullable
    private CompoundTag pendingTag;
    private float pendingMaxHp;
    /** 捕獲時の分類ベース値・Tier加算 (揺れ抽選用に保持) */
    private int pendingBase;
    private int pendingBonus;
    @Nullable
    private UUID ownerId;

    public CapsuleBallEntity(EntityType<? extends CapsuleBallEntity> type, Level level) {
        super(type, level);
    }

    public CapsuleBallEntity(Level level, LivingEntity owner, ItemStack stack, int mode) {
        this(BambooEntities.CAPSULE_BALL.get(), level);
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        this.setItem(stack.copyWithCount(1));
        this.entityData.set(DATA_MODE, mode);
        this.entityData.set(DATA_TIER, CapsuleBallItem.tierOf(stack).ordinal());
        if (owner.getUUID() != null) this.ownerId = owner.getUUID();
    }

    public int getMode() {
        try {
            return this.entityData.get(DATA_MODE);
        } catch (Exception e) {
            return MODE_CAPTURE;
        }
    }

    public CapsuleTier getTier() {
        try {
            int ord = this.entityData.get(DATA_TIER);
            CapsuleTier[] values = CapsuleTier.values();
            if (ord < 0 || ord >= values.length) return CapsuleTier.N;
            return values[ord];
        } catch (Exception e) {
            return CapsuleTier.N;
        }
    }

    private boolean isShaking() {
        return pendingTag != null;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_MODE, MODE_CAPTURE);
        this.entityData.define(DATA_SHAKE, 0);
        this.entityData.define(DATA_TIER, CapsuleTier.N.ordinal());
    }

    @Override
    protected net.minecraft.world.item.Item getDefaultItem() {
        try {
            return ruby.bamboo.core.init.BambooItems.CAPSULE_BALL.get();
        } catch (Exception e) {
            return net.minecraft.world.item.Items.SNOWBALL;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CapsuleMode", getMode());
        tag.putInt("CapsuleShake", this.entityData.get(DATA_SHAKE));
        tag.putInt("CapsuleTier", getTier().ordinal());
        tag.putInt("CapsuleShakeTimer", shakeTimer);
        tag.putInt("CapsuleBase", pendingBase);
        tag.putInt("CapsuleBonus", pendingBonus);
        tag.putFloat("CapsuleMaxHp", pendingMaxHp);
        if (pendingTag != null) tag.put("CapsulePending", pendingTag.copy());
        if (ownerId != null) tag.putUUID("CapsuleOwner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_MODE, tag.getInt("CapsuleMode"));
        this.entityData.set(DATA_TIER, tag.getInt("CapsuleTier"));
        this.entityData.set(DATA_SHAKE, tag.getInt("CapsuleShake"));
        this.shakeTimer = tag.getInt("CapsuleShakeTimer");
        this.pendingBase = tag.getInt("CapsuleBase");
        this.pendingBonus = tag.getInt("CapsuleBonus");
        this.pendingMaxHp = tag.getFloat("CapsuleMaxHp");
        if (tag.contains("CapsulePending")) this.pendingTag = tag.getCompound("CapsulePending").copy();
        if (tag.hasUUID("CapsuleOwner")) this.ownerId = tag.getUUID("CapsuleOwner");
        if (isShaking()) this.setNoGravity(true);
    }

    @Nullable
    private ServerPlayer serverOwner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return null;
        Entity e = serverLevel.getEntity(ownerId);
        // 投げ主がプレイヤーとは限らない想定でEntity基準。Player取得は別途
        if (e instanceof ServerPlayer player) return player;
        var p = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        return p;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide) return;
        if (getMode() == MODE_SUMMON) return; // 召喚中はすり抜け
        if (isShaking()) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        if (target == getOwner()) return;
        if (!target.isAlive() || target.isRemoved()) return;
        CapsuleTier tier = getTier();
        CapsuleRules.Category category = CapsuleRules.classify(target, ownerId);
        if (category == CapsuleRules.Category.UNCAPTURABLE) {
            // 消費せず落とす
            dropThrown();
            ServerPlayer owner = serverOwner();
            if (owner != null) {
                owner.displayClientMessage(
                        Component.translatable("message.bamboomod.capsule_ball_uncapturable"), true);
            }
            this.discard();
            return;
        }
        // 吸収 (揺れ中の被撃・二重化防止のため即discard)
        target.ejectPassengers();
        target.stopRiding();
        var captured = CapsuleRules.captureOf(target);
        pendingTag = captured.getData();
        pendingMaxHp = captured.getMaxHp();
        pendingBase = CapsuleRules.baseRate(category);
        pendingBonus = tier.bonus;
        target.discard();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.shakeTimer = 0;
        this.entityData.set(DATA_SHAKE, 0);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 0.3D, getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide) return;
        if (isShaking()) return;
        if (getMode() == MODE_SUMMON) {
            doSummon(hit.getLocation().add(0.0D, 0.5D, 0.0D));
            this.discard();
            return;
        }
        // 外した捕獲ボールは消費せず落とす
        dropThrown();
        this.discard();
    }

    private void dropThrown() {
        ItemStack stack = this.getItem().copy();
        if (stack.isEmpty()) return;
        ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), stack);
        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
    }

    /**
     * 召喚モードの実体化。ボールは投擲時に消費していないため、
     * 投げ主の対応ボール (BoundId一致の捕獲済み) をその場で紐付け空へ変える。
     * クリエイティブ (非消費) でもサバイバルでも同じ経路で成立する。
     */
    private void doSummon(Vec3 at) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ItemStack stack = this.getItem().copy();
        UUID bound = CapsuleRules.getBoundId(stack);
        ServerPlayer owner = serverOwner();
        if (bound != null) {
            // 二重召喚防止: 既に出ている個体がいたら何もしない
            for (ServerLevel lvl : serverLevel.getServer().getAllLevels()) {
                Entity existing = lvl.getEntity(bound);
                if (existing != null && !existing.isRemoved()) {
                    if (owner != null) {
                        owner.displayClientMessage(Component
                                .translatable("message.bamboomod.capsule_ball_already_out"), true);
                    }
                    return;
                }
            }
        }
        Entity entity = CapsuleRules.spawnReleased(serverLevel, stack, at, getYRot());
        if (entity == null) {
            serverLevel.playSound(null, at.x, at.y, at.z,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.4F, 1.5F);
            return;
        }
        boolean converted = false;
        if (owner != null && bound != null) {
            // 手持ち (両手+インベントリ) の対応ボールを紐付け空へ
            for (ItemStack inv : owner.getInventory().items) {
                if (isMatchingCaptured(inv, bound)) {
                    inv.getOrCreateTag().putBoolean(CapsuleRules.TAG_CAPTURED, false);
                    converted = true;
                    break;
                }
            }
            if (!converted && isMatchingCaptured(owner.getOffhandItem(), bound)) {
                owner.getOffhandItem().getOrCreateTag().putBoolean(CapsuleRules.TAG_CAPTURED, false);
                converted = true;
            }
            if (!converted && !owner.getAbilities().instabuild) {
                // 対応ボールが見当たらない (投げてから移動した等): 紐付け空を渡す
                ItemStack back = stack.copy();
                back.setCount(1);
                back.getOrCreateTag().putBoolean(CapsuleRules.TAG_CAPTURED, false);
                if (!owner.getInventory().add(back)) {
                    ItemEntity drop = new ItemEntity(serverLevel, owner.getX(),
                            owner.getY() + 0.5D, owner.getZ(), back);
                    drop.setDefaultPickUpDelay();
                    serverLevel.addFreshEntity(drop);
                }
            }
            // NBTのみの書き換え・追加は明示的に同期する
            owner.inventoryMenu.broadcastChanges();
        }
        serverLevel.playSound(null, at.x, at.y, at.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                at.x, at.y + 0.5D, at.z, 12, 0.4D, 0.5D, 0.4D, 0.05D);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.bamboomod.capsule_ball_summon",
                    entity.getType().getDescription().getString()), true);
        }
    }

    /** 対応する捕獲済みボールか (その場変換用)。 */
    private static boolean isMatchingCaptured(ItemStack stack, UUID bound) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CapsuleBallItem)) return false;
        if (!CapsuleRules.isCaptured(stack)) return false;
        return bound.equals(CapsuleRules.getBoundId(stack));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (isShaking()) {
            tickShaking();
            return;
        }
        // 飛行 failsafe
        int timeout = getMode() == MODE_SUMMON ? FLY_TIMEOUT_SUMMON : FLY_TIMEOUT_CAPTURE;
        if (this.tickCount > timeout) {
            if (getMode() == MODE_SUMMON) {
                doSummon(position().add(0.0D, 0.5D, 0.0D));
            } else {
                dropThrown();
            }
            this.discard();
        }
    }

    private void tickShaking() {
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(this.getYRot() + 25.0F);
        shakeTimer++;
        // 吸収個体の保存タグが壊れていたら復元不能のため破砕扱いで終了
        if (pendingTag == null) {
            this.discard();
            return;
        }
        // チャンク跨ぎ等の長期化で個体ロストさせない (10秒で復元)
        if (shakeTimer > SHAKE_TICKS * SHAKE_COUNT + 200) {
            restoreTarget();
            this.discard();
            return;
        }
        if (shakeTimer % SHAKE_TICKS != 0) return;
        int stage = shakeTimer / SHAKE_TICKS; // 1..3
        if (stage < 1 || stage > SHAKE_COUNT) return;
        float hp = pendingTag.getFloat("Health");
        float p = CapsuleRules.probability(pendingBase, pendingBonus, hp, pendingMaxHp);
        boolean success = level().getRandom().nextFloat() < p;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (success) {
            this.entityData.set(DATA_SHAKE, stage);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F + 0.2F * stage);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    getX(), getY() + 0.3D, getZ(), 6, 0.2D, 0.2D, 0.2D, 0.05D);
            if (stage >= SHAKE_COUNT) {
                completeCapture(serverLevel);
            }
            return;
        }
        // 失敗: 個体復元 + ボール消費
        restoreTarget();
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.2F);
        serverLevel.sendParticles(ParticleTypes.POOF,
                getX(), getY() + 0.3D, getZ(), 10, 0.25D, 0.25D, 0.25D, 0.05D);
        ServerPlayer owner = serverOwner();
        if (owner != null) {
            owner.displayClientMessage(
                    Component.translatable("message.bamboomod.capsule_ball_fail"), true);
        }
        this.discard();
    }

    /** 吸収中タグから個体をその場に復元する (使い切り)。 */
    private void restoreTarget() {
        CompoundTag tag = pendingTag;
        pendingTag = null;
        if (tag == null || !(level() instanceof ServerLevel serverLevel)) return;
        try {
            var opt = EntityType.byString(tag.getString("id"));
            if (opt.isEmpty()) return;
            Entity entity = opt.get().create(serverLevel);
            if (entity == null) return;
            CompoundTag data = tag.copy();
            data.remove("id");
            entity.load(data);
            entity.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
            serverLevel.addFreshEntity(entity);
        } catch (Exception e) {
            ruby.bamboo.BambooMod.LOGGER.warn("CapsuleBall restore failed", e);
        }
    }

    /** 3回全成功: 捕獲済みボールを投擲者へ付与。 */
    private void completeCapture(ServerLevel serverLevel) {
        CompoundTag tag = pendingTag;
        pendingTag = null;
        if (tag == null) {
            this.discard();
            return;
        }
        CapsuleTier tier = getTier();
        if (ownerId == null) {
            // 投げ主不明のフォールバック: 個体を復元して終了
            pendingTag = tag;
            restoreTarget();
            this.discard();
            return;
        }
        ItemStack filled;
        try {
            filled = new ItemStack(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(CapsuleRules.itemIdOf(tier)));
        } catch (Exception e) {
            filled = ItemStack.EMPTY;
        }
        if (filled.isEmpty()) {
            // フォールバック: 投擲スタック自体へ書き込む
            filled = this.getItem().copy();
            filled.setCount(1);
        }
        float maxHp = pendingMaxHp > 0.0F ? pendingMaxHp : tag.getFloat("Health");
        CapsuleRules.fillBallFromTag(filled,
                new CapsuleRules.CompoundTagWrapper(tag.getUUID("UUID"), tag.getString("id"),
                        tag.copy(), maxHp),
                tier, ownerId);
        serverLevel.playSound(null, getX(), getY(), getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                getX(), getY() + 0.5D, getZ(), 14, 0.3D, 0.4D, 0.3D, 0.05D);
        ServerPlayer player = serverOwner();
        if (player != null) {
            if (!player.getInventory().add(filled.copy())) {
                ItemEntity drop = new ItemEntity(serverLevel, player.getX(),
                        player.getY() + 0.5D, player.getZ(), filled.copy());
                drop.setDefaultPickUpDelay();
                serverLevel.addFreshEntity(drop);
            }
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable("message.bamboomod.capsule_ball_success",
                    CapsuleRules.describeEntityId(tag.getString("id"))), false);
        } else {
            ItemEntity drop = new ItemEntity(serverLevel, getX(), getY() + 0.5D, getZ(), filled.copy());
            drop.setDefaultPickUpDelay();
            serverLevel.addFreshEntity(drop);
        }
        this.discard();
    }
}
