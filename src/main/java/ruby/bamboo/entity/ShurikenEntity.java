package ruby.bamboo.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.item.ShurikenItem;

/**
 * 手裏剣エンティティ (sakura ShurikenEntity の 1.20.1 移植)。
 * AbstractArrow 継承、無重力+手動降下、回転、追尾、多段、毒/炎等。
 */
public class ShurikenEntity extends AbstractArrow {

    private static final EntityDataAccessor<ItemStack> ITEMSTACK = SynchedEntityData.defineId(ShurikenEntity.class, EntityDataSerializers.ITEM_STACK);

    private int timer = 0;
    public float xAngle = this.random.nextFloat() * 90 - 45;

    private final Set<MobEffectInstance> customPotionEffects = new HashSet<>();

    // infinity tier: 0 none, 1 stone, 2 iron, 3 diamond
    private int infinity = 0;
    private static final int STONE = 1;
    private static final int IRON = 2;
    private static final int DIAMOND = 3;

    private boolean isReturn = false;
    private int multiThrow = 0;
    private float velocity;
    private float inaccuracy;
    private int snipeLevel = 0;

    public ShurikenEntity(EntityType<? extends ShurikenEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public ShurikenEntity(Level level, LivingEntity shooter) {
        super(BambooEntities.SHURIKEN.get(), shooter, level, new ItemStack(BambooItems.SHURIKEN_STONE.get()), null);
        this.setNoGravity(true);
    }

    @Override
    public void shootFromRotation(Entity shooter, float pitch, float yaw, float p_184547_4_, float velocity, float inaccuracy) {
        super.shootFromRotation(shooter, pitch, yaw, p_184547_4_, velocity, inaccuracy);
        this.velocity = velocity;
        this.inaccuracy = inaccuracy;
        // クリティカルのパーティクルが邪魔なので少し下げと補正 (sakura)
        this.setPos(this.getX(), this.getY() - 0.25D, this.getZ());
        this.setDeltaMovement(this.getDeltaMovement().add(0, 0.05, 0));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ITEMSTACK, ItemStack.EMPTY);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockPos blockPos = result.getBlockPos();
        BlockState state = this.level().getBlockState(blockPos);
        Vec3 vec3d = result.getLocation().subtract(this.getX(), this.getY(), this.getZ());
        this.setDeltaMovement(vec3d);
        Vec3 vec3d1 = vec3d.normalize().scale(0.05D);
        this.setPos(this.getX() - vec3d1.x, this.getY() - vec3d1.y, this.getZ() - vec3d1.z);
        this.playSound(state.getSoundType(level(), blockPos, this).getHitSound(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.inGround = true;
        this.shakeTime = 7;
        this.setCritArrow(false);
        this.setSoundEvent(SoundEvents.ARROW_HIT);
        // 1.21: shotFromCrossbow は firedFromWeapon 由来の getter のみ (setter 廃止)。手裏剣は null のため常に false。
        state.onProjectileHit(this.level(), state, result, this);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            Entity shooter = this.getOwner();
            if (this.multiThrow > 0) {
                this.multiThrow();
            }
            if (this.multiThrow < 0) {
                if (target instanceof LivingEntity le) {
                    le.invulnerableTime = 0;
                }
            }
            ItemStack arrowStack = this.getPickupItemStackOrigin();
            float dmg = 2.0F;
            if (arrowStack.getItem() instanceof ShurikenItem shuriken) {
                dmg = shuriken.getAttackDamage(arrowStack);
            }
            if (this.isCritArrow()) {
                dmg *= 1.5F;
            }
            dmg += (float) this.getBaseDamage();

            if (target.hurt(this.damageSources().arrow(this, shooter != null ? shooter : this), dmg)) {
                if (shooter instanceof LivingEntity livingShooter) {
                    livingShooter.setLastHurtMob(target);
                }
                if (this.isOnFire() && !(target instanceof net.minecraft.world.entity.monster.EnderMan)) {
                    target.setRemainingFireTicks(5);
                }
                if (target instanceof LivingEntity livingTarget && !this.customPotionEffects.isEmpty()) {
                    for (MobEffectInstance eff : this.customPotionEffects) {
                        livingTarget.addEffect(new MobEffectInstance(eff));
                    }
                }
                this.discard();
            }
        }
    }

    @Override
    public void tick() {
        this.multiThrow();

        this.timer++;
        float tempPitch = this.getXRot();
        float tempYaw = this.getYRot();
        super.tick();
        this.setXRot(tempPitch);
        this.setYRot(tempYaw);
        this.xRotO = tempPitch;
        this.yRotO = tempYaw;

        if (!this.inGround) {
            this.setXRot(this.getXRot() - 36);
        } else {
            if (!this.level().isClientSide) {
                if (this.isReturn && this.getOwner() instanceof Player p) {
                    this.playerTouch(p);
                }
            }
        }
        // 重力手動
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, motion.y - 0.03, motion.z);

        // snipe追尾 (lv1-3、lvあたり0.3ブロック補正を拡張、エイムアシスト強化)
        if (this.snipeLevel > 0 && !this.inGround) {
            LivingEntity target = findSnipeTarget();
            if (target != null) {
                Vec3 toTarget = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(this.position()).normalize();
                Vec3 cur = this.getDeltaMovement().normalize();
                // 旧 0.02*lv は弱すぎたため 0.10~0.18 に強化 (約5倍)
                double lerp = 0.10 + (this.snipeLevel - 1) * 0.04; // lv1:0.10, lv2:0.14, lv3:0.18
                Vec3 blended = cur.lerp(toTarget, lerp).normalize();
                double speed = this.getDeltaMovement().length();
                this.setDeltaMovement(blended.scale(speed));
            }
        }
    }

    private LivingEntity findSnipeTarget() {
        Entity owner = this.getOwner();
        if (owner == null) return null;
        // 半径24に拡張、前方かつ横ズレが threshold 以内の最も近いLivingを優先
        double range = 24.0;
        var aabb = this.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestLateral = Double.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, aabb, e2 -> e2 != owner && e2.isAlive() && !e2.isSpectator())) {
            Vec3 toE = e.position().add(0, e.getEyeHeight() * 0.5, 0).subtract(this.position());
            double distSqr = this.distanceToSqr(e);
            // 前方判定: 内積が正 (=前方) かつ沿い距離が range 以内
            Vec3 dir = this.getDeltaMovement().normalize();
            double dot = toE.normalize().dot(dir);
            if (dot <= 0) continue; // 後方は除外
            // 横ズレ = |toE x dir| (dirは正規化)
            double lateral = toE.cross(dir).length();
            // 沿い距離 = toE・dir
            double forward = toE.dot(dir);
            if (forward < 0 || forward > range) continue;
            // しきい値: lv1:1.5, lv2:2.5, lv3:3.5 ブロック（旧0.3/lvでは狭すぎ）
            // 10ブロック先で 8.5°/14°/19° 相当、体感しやすい範囲
            double threshold = 1.5 + (this.snipeLevel - 1) * 1.0;
            if (lateral > threshold) continue;
            // 横ズレが小さいものを優先、同点なら距離が近いもの
            if (lateral < bestLateral - 0.1 || (Math.abs(lateral - bestLateral) < 0.1 && distSqr < bestDist)) {
                best = e;
                bestLateral = lateral;
                bestDist = distSqr;
            }
        }
        return best;
    }

    private void multiThrow() {
        if (!this.level().isClientSide) {
            if (this.timer % 3 == 1) {
                if (this.multiThrow > 0) {
                    Entity shooter = this.getOwner();
                    if (shooter instanceof LivingEntity livingShooter) {
                        ItemStack stack = this.pickupInventoryShuriken();
                        if (!stack.isEmpty()) {
                            ShurikenEntity entity = new ShurikenEntity(this.level(), livingShooter);
                            entity.setItemStack(stack);
                            entity.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0, this.velocity, this.inaccuracy);
                            entity.multiThrow = --this.multiThrow != 0 ? this.multiThrow : -1;
                            entity.pickup = this.pickup;
                            entity.infinity = this.infinity;
                            entity.setCritArrow(this.isCritArrow());
                            entity.setEffects(this.customPotionEffects);
                            entity.setIsReturn(this.isReturn);
                            entity.setSnipeLevel(this.snipeLevel);
                            entity.setBaseDamage(this.getBaseDamage() * 0.7D);
                            if (this.isOnFire()) entity.setRemainingFireTicks(100);
                            this.level().addFreshEntity(entity);
                            this.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
                            this.multiThrow = -1;
                        } else {
                            this.multiThrow = 0;
                        }
                    } else {
                        this.multiThrow = 0;
                    }
                }
            }
        }
    }

    @Nonnull
    private ItemStack pickupInventoryShuriken() {
        if (this.infinity > 0) {
            if (this.infinity == DIAMOND) return new ItemStack(BambooItems.SHURIKEN_DIAMOND.get());
            if (this.infinity == IRON) return new ItemStack(BambooItems.SHURIKEN_IRON.get());
            return new ItemStack(BambooItems.SHURIKEN_STONE.get());
        }
        if (this.getOwner() instanceof Player player) {
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty() && s.getItem() instanceof ShurikenItem) {
                    if (player.getAbilities().instabuild) {
                        return s.copyWithCount(1);
                    }
                    if (this.isNoPickup()) {
                        ItemStack copy = s.copy();
                        copy.setCount(1);
                        return copy;
                    } else {
                        return s.split(1);
                    }
                }
            }
            for (ItemStack s : player.getInventory().offhand) {
                if (!s.isEmpty() && s.getItem() instanceof ShurikenItem) {
                    if (player.getAbilities().instabuild) return s.copyWithCount(1);
                    if (this.isNoPickup()) {
                        ItemStack copy = s.copy(); copy.setCount(1); return copy;
                    } else return s.split(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide && (this.inGround || this.isNoPhysics()) && this.shakeTime <= 0) {
            boolean flag = this.pickup == Pickup.ALLOWED || this.pickup == Pickup.CREATIVE_ONLY && player.getAbilities().instabuild || this.isNoPhysics() && this.getOwner() != null && this.getOwner().getUUID().equals(player.getUUID());
            if (!player.getAbilities().instabuild && !this.isNoPickup()) {
                if (this.pickup == Pickup.ALLOWED && !player.getInventory().add(this.getPickupItem())) {
                    flag = false;
                }
            } else {
                flag = true;
            }
            if (flag) {
                player.take(this, 1);
                this.discard();
            } else if (this.isReturn) {
                this.discard();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack stack = this.getPickupItemStackOrigin();
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(this.registryAccess()));
        }
        if (!this.customPotionEffects.isEmpty()) {
            ListTag list = new ListTag();
            for (MobEffectInstance eff : this.customPotionEffects) {
                list.add(eff.save());
            }
            tag.put("CustomPotionEffects", list);
        }
        tag.putInt("infinity", this.infinity);
        tag.putInt("multiThrow", this.multiThrow);
        tag.putBoolean("isReturn", this.isReturn);
        tag.putFloat("velocity", this.velocity);
        tag.putFloat("inaccuracy", this.inaccuracy);
        tag.putInt("timer", this.timer);
        tag.putInt("snipeLevel", this.snipeLevel);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Item")) {
            ItemStack stack = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Item"));
            this.setItemStack(stack);
        }
        this.customPotionEffects.clear();
        if (tag.contains("CustomPotionEffects")) {
            ListTag list = tag.getList("CustomPotionEffects", 10);
            for (int i = 0; i < list.size(); i++) {
                MobEffectInstance eff = MobEffectInstance.load(list.getCompound(i));
                if (eff != null) this.customPotionEffects.add(eff);
            }
        }
        this.infinity = tag.getInt("infinity");
        this.multiThrow = tag.getInt("multiThrow");
        this.isReturn = tag.getBoolean("isReturn");
        this.velocity = tag.getFloat("velocity");
        this.inaccuracy = tag.getFloat("inaccuracy");
        this.timer = tag.getInt("timer");
        this.snipeLevel = tag.getInt("snipeLevel");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return super.getAddEntityPacket(entity);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(BambooItems.SHURIKEN_STONE.get());
    }

    @Override
    protected ItemStack getPickupItem() {
        ItemStack stack = this.getPickupItemStackOrigin();
        return stack.isEmpty() ? new ItemStack(BambooItems.SHURIKEN_STONE.get()) : stack;
    }

    public ItemStack getPickupItemStackOrigin() {
        return this.entityData.get(ITEMSTACK);
    }

    public void setItemStack(ItemStack stack) {
        if (stack.getItem() != this.getPickupItemStackOrigin().getItem() || stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            this.entityData.set(ITEMSTACK, copy);
        }
    }

    public ItemStack getItem() {
        ItemStack stack = this.getPickupItemStackOrigin();
        return stack.isEmpty() ? new ItemStack(BambooItems.SHURIKEN_STONE.get()) : stack;
    }

    public void setMultiThrow(int lvl) {
        this.multiThrow = lvl;
    }

    public void addEffect(MobEffectInstance eff) {
        this.customPotionEffects.add(eff);
    }

    public void setEffects(Collection<MobEffectInstance> effects) {
        this.customPotionEffects.clear();
        this.customPotionEffects.addAll(effects);
    }

    public Set<MobEffectInstance> getEffects() {
        return this.customPotionEffects;
    }

    public void setIsReturn(boolean v) {
        this.isReturn = v;
    }

    public void setInfinity(Tier tier) {
        if (tier == Tiers.STONE) this.infinity = STONE;
        else if (tier == Tiers.IRON) this.infinity = IRON;
        else if (tier == Tiers.DIAMOND) this.infinity = DIAMOND;
        else this.infinity = 0;
    }

    public void setSnipeLevel(int lv) { this.snipeLevel = lv; }
    public int getSnipeLevel() { return this.snipeLevel; }

    public void setNonPickup() {
        this.pickup = Pickup.DISALLOWED;
    }

    public boolean isNoPickup() {
        return this.pickup == Pickup.DISALLOWED;
    }
}
