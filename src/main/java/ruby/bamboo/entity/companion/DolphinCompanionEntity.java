package ruby.bamboo.entity.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * イルカ仲間 - 蔵無し・染料染色・視線追従3D移動・水中追従。
 */
public class DolphinCompanionEntity extends Dolphin {

    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(DolphinCompanionEntity.class, EntityDataSerializers.INT);
    private static final String TAG_COLOR = "DolphinColor";
    private static final String TAG_HOME_POS = "HomePos";
    private static final String TAG_OWNER = "Owner";

    private BlockPos homePos;
    @Nullable
    private UUID ownerUUID;

    public DolphinCompanionEntity(EntityType<? extends Dolphin> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.2D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_COLOR, DyeColor.WHITE.getId());
    }

    public DyeColor getDolphinColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLOR));
    }

    public void setDolphinColor(DyeColor color) {
        this.entityData.set(DATA_COLOR, color.getId());
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_COLOR, getDolphinColor().getId());
        if (homePos != null) tag.putLong(TAG_HOME_POS, homePos.asLong());
        if (ownerUUID != null) tag.putUUID(TAG_OWNER, ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_COLOR)) setDolphinColor(DyeColor.byId(tag.getInt(TAG_COLOR)));
        if (tag.contains(TAG_HOME_POS)) homePos = BlockPos.of(tag.getLong(TAG_HOME_POS));
        if (tag.hasUUID(TAG_OWNER)) ownerUUID = tag.getUUID(TAG_OWNER);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    public boolean canBeControlledByRider() {
        return this.getControllingPassenger() instanceof Player;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        var e = this.getFirstPassenger();
        if (e instanceof Player p) return p;
        return null;
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isVehicle()) {
            if (ownerUUID != null) {
                var owner = this.level().getPlayerByUUID(ownerUUID);
                if (owner != null && owner.isInWater() && this.isInWater()) {
                    double dist = this.distanceTo(owner);
                    if (dist > 4.0 && dist < 64) {
                        this.getNavigation().moveTo(owner, 1.4D);
                    }
                    this.setPersistenceRequired();
                    return;
                }
            }
            if (this.isInWater() && homePos != null) {
                double distSq = this.blockPosition().distSqr(homePos);
                if (distSq > 256) {
                    this.getNavigation().moveTo(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, 1.1D);
                }
            }
        }
        this.setPersistenceRequired();
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive() && this.isVehicle() && this.canBeControlledByRider()) {
            var rider = (Player) this.getControllingPassenger();
            if (rider != null) {
                this.setYRot(rider.getYRot());
                this.yRotO = this.getYRot();
                this.setXRot(rider.getXRot() * 0.5F);
                this.setRot(this.getYRot(), this.getXRot());
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.yBodyRot;

                float f = rider.zza;
                float f1 = rider.xxa;
                if (f <= 0.0F) f *= 0.25F; else f *= 0.6F;
                f1 *= 0.1F;

                if (this.isInWater()) {
                    Vec3 look = rider.getLookAngle();
                    Vec3 forward = look.scale(f * 0.25);
                    // 左右逆だったため符号反転、後退と同程度の低速に (f1は0.5済み、0.12→実効0.06で後退0.0625と同等)
                    Vec3 strafe;
                    double horizLen2 = look.x * look.x + look.z * look.z;
                    if (horizLen2 < 1.0E-4) {
                        strafe = Vec3.ZERO;
                    } else {
                        // 右ベクトルは look × up = (-look.z,0,look.x) だが逆だったので反転
                        strafe = new Vec3(look.z, 0, -look.x).normalize().scale(f1 * 0.12);
                    }
                    Vec3 motion = forward.add(strafe);
                    Vec3 cur = this.getDeltaMovement();
                    Vec3 next = cur.add(motion).scale(0.92);
                    this.setDeltaMovement(next);
                    this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
                } else {
                    super.travel(new Vec3(f1, travelVector.y, f));
                }
                return;
            }
        }
        super.travel(travelVector);
    }

    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, net.minecraft.world.entity.Entity.MoveFunction moveFunc) {
        super.positionRider(passenger, moveFunc);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 餌や染料の Use を騎乗より優先
        if (isFish(stack)) {
            if (this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                this.heal(4.0F);
                this.playSound(SoundEvents.DOLPHIN_EAT, 1.0F, 1.0F);
                if (!this.level().isClientSide) {
                    ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                            this.getX(), this.getY() + 0.8, this.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        DyeColor dye = DyeColor.getColor(stack);
        if (dye != null) {
            if (dye != getDolphinColor()) {
                setDolphinColor(dye);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                this.playSound(SoundEvents.DYE_USE, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!player.isShiftKeyDown() && this.getPassengers().isEmpty()) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
                this.homePos = null;
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private static boolean isFish(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try { if (stack.is(net.minecraft.tags.ItemTags.FISHES)) return true; } catch (Exception ignored) {}
        return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH)
                || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON);
    }

    @Override
    public void ejectPassengers() {
        super.ejectPassengers();
        if (!this.level().isClientSide && this.isInWater()) {
            this.homePos = this.blockPosition();
        }
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
        super.dropAllDeathLoot(source);
    }
}
