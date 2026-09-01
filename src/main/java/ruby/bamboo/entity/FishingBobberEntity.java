package ruby.bamboo.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.core.init.BambooEntities;

import java.util.UUID;

/**
 * Fishing bobber. Cast, bob, return with optional hooked item.
 */
public class FishingBobberEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_X = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_Y = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ANCHOR_Z = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(FishingBobberEntity.class, EntityDataSerializers.BOOLEAN);

    private Player owner;
    private UUID ownerUUID;
    private Vec3 anchor = Vec3.ZERO;
    private int returnTicks = 0;

    public FishingBobberEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public FishingBobberEntity(Level level, Player owner, Vec3 pos) {
        this(BambooEntities.FISHING_BOBBER.get(), level);
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
        setAnchorSynced(pos);
        this.setPos(pos);
        this.setNoGravity(true);
    }

    private void setAnchorSynced(Vec3 v) {
        this.anchor = v;
        try {
            this.entityData.set(DATA_ANCHOR_X, (float) v.x);
            this.entityData.set(DATA_ANCHOR_Y, (float) v.y);
            this.entityData.set(DATA_ANCHOR_Z, (float) v.z);
        } catch (Exception ignored) {}
    }

    public void setOwner(Player player) {
        this.owner = player;
        if (player != null) {
            this.ownerUUID = player.getUUID();
            try {
                this.entityData.set(DATA_OWNER_ID, player.getId());
            } catch (Exception ignored) {}
        }
    }

    public Player getOwnerPlayer() {
        if (owner != null && !owner.isRemoved()) return owner;
        int id = -1;
        try { id = this.entityData.get(DATA_OWNER_ID); } catch (Exception ignored) {}
        if (id != -1) {
            Entity e = level().getEntity(id);
            if (e instanceof Player p) {
                owner = p;
                if (ownerUUID == null) ownerUUID = p.getUUID();
                return p;
            }
        }
        if (ownerUUID != null && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Entity e = sl.getEntity(ownerUUID);
            if (e instanceof Player p) {
                owner = p;
                return p;
            }
        }
        try {
            for (Player p : level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64))) {
                if (p.getUUID().equals(ownerUUID)) { owner = p; return p; }
            }
        } catch (Exception ignored) {}
        return owner;
    }

    public Vec3 getAnchor() {
        if (level().isClientSide) {
            try {
                float x = this.entityData.get(DATA_ANCHOR_X);
                float y = this.entityData.get(DATA_ANCHOR_Y);
                float z = this.entityData.get(DATA_ANCHOR_Z);
                if (x == 0 && y == 0 && z == 0) {
                    if (anchor != Vec3.ZERO) return anchor;
                    return this.position();
                }
                return new Vec3(x, y, z);
            } catch (Exception e) {
                return anchor != Vec3.ZERO ? anchor : this.position();
            }
        }
        return anchor;
    }

    public boolean isReturning() {
        try { return this.entityData.get(DATA_RETURNING); } catch (Exception e) { return false; }
    }

    public void setReturning(boolean v) {
        try { this.entityData.set(DATA_RETURNING, v); } catch (Exception ignored) {}
    }

    public ItemStack getCarried() {
        try { return this.entityData.get(DATA_CARRIED); } catch (Exception e) { return ItemStack.EMPTY; }
    }

    public void setCarried(ItemStack stack) {
        try { this.entityData.set(DATA_CARRIED, stack.copy()); } catch (Exception ignored) {}
    }

    public void startReturn(ItemStack carried) {
        if (carried != null && !carried.isEmpty()) setCarried(carried);
        else setCarried(ItemStack.EMPTY);
        setReturning(true);
        returnTicks = 0;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof net.minecraft.world.entity.item.ItemEntity;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.35;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    private boolean isHoldingRod(Player p) {
        try {
            var rod = ruby.bamboo.core.init.BambooItems.BAMBOO_ROD.get();
            return p.getMainHandItem().is(rod) || p.getOffhandItem().is(rod);
        } catch (Exception e) {
            return p.getMainHandItem().getItem() instanceof ruby.bamboo.item.BambooRodItem
                    || p.getOffhandItem().getItem() instanceof ruby.bamboo.item.BambooRodItem;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER_ID, -1);
        this.entityData.define(DATA_ANCHOR_X, 0.0F);
        this.entityData.define(DATA_ANCHOR_Y, 0.0F);
        this.entityData.define(DATA_ANCHOR_Z, 0.0F);
        this.entityData.define(DATA_CARRIED, ItemStack.EMPTY);
        this.entityData.define(DATA_RETURNING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (owner != null && this.entityData.get(DATA_OWNER_ID) == -1) {
            try { this.entityData.set(DATA_OWNER_ID, owner.getId()); } catch (Exception ignored) {}
        }
        if (!level().isClientSide && anchor != Vec3.ZERO && !isReturning()) {
            try {
                this.entityData.set(DATA_ANCHOR_X, (float) anchor.x);
                this.entityData.set(DATA_ANCHOR_Y, (float) anchor.y);
                this.entityData.set(DATA_ANCHOR_Z, (float) anchor.z);
            } catch (Exception ignored) {}
        }

        if (isReturning()) {
            Player p = getOwnerPlayer();
            if (p == null || p.isRemoved() || !p.isAlive() || p.level() != this.level()) {
                if (!level().isClientSide) {
                    dropAllPassengersAsItems();
                    this.discard();
                }
                return;
            }
            if (!level().isClientSide && !isHoldingRod(p)) {
                dropAllPassengersAsItems();
                this.discard();
                return;
            }
            if (!level().isClientSide) {
                returnTicks++;
                if (returnTicks > 20) {
                    dropAllPassengersAsItems();
                    this.discard();
                    return;
                }
            }
            Vec3 target = new Vec3(p.getX(), p.getY() + 0.9, p.getZ());
            Vec3 pos = this.position();
            Vec3 to = target.subtract(pos);
            double dist = to.length();
            if (dist < 0.7) {
                if (!level().isClientSide) {
                    for (Entity passenger : this.getPassengers()) {
                        if (passenger instanceof ItemEntity item) {
                            ItemStack stack = item.getItem().copy();
                            boolean added = p.getInventory().add(stack);
                            if (!added) {
                                ItemEntity drop = new ItemEntity(level(), p.getX(), p.getY() + 0.5, p.getZ(), stack);
                                drop.setPickUpDelay(0);
                                level().addFreshEntity(drop);
                            } else {
                                level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
                            }
                        }
                        passenger.discard();
                    }
                    ItemStack carried = getCarried();
                    if (!carried.isEmpty()) {
                        boolean added = p.getInventory().add(carried.copy());
                        if (!added) {
                            ItemEntity drop = new ItemEntity(level(), p.getX(), p.getY() + 0.5, p.getZ(), carried.copy());
                            drop.setPickUpDelay(0);
                            level().addFreshEntity(drop);
                        }
                        setCarried(ItemStack.EMPTY);
                    }
                    this.discard();
                } else {
                    this.setPos(target.x, target.y, target.z);
                }
                return;
            }
            double speed = 0.45 + Math.min(dist * 0.12, 0.85);
            Vec3 motion = to.normalize().scale(speed);
            motion = motion.add(0, 0.06, 0);
            if (!level().isClientSide) {
                this.setPos(pos.add(motion));
                this.setDeltaMovement(motion);
            } else {
                this.setPos(pos.add(motion.scale(0.85)));
            }
            if (!level().isClientSide && p.distanceTo(this) > 64) {
                dropAllPassengersAsItems();
                this.discard();
            }
            return;
        }

        if (!level().isClientSide && anchor != Vec3.ZERO) {
            this.setPos(anchor.x, anchor.y, anchor.z);
        } else if (level().isClientSide) {
            Vec3 a = getAnchor();
            double bob = Math.sin(tickCount * 0.18) * 0.015;
            this.setPos(a.x, a.y + bob, a.z);
        } else {
            double bob = Math.sin(tickCount * 0.18) * 0.015;
            this.setPos(anchor.x, anchor.y + bob, anchor.z);
        }

        if (!level().isClientSide) {
            Player p = getOwnerPlayer();
            if (p == null || p.isRemoved() || !p.isAlive()) {
                this.discard();
                return;
            }
            if (!isHoldingRod(p)) {
                this.discard();
                return;
            }
            if (p.distanceTo(this) > 64) {
                this.discard();
                return;
            }
            if (p.level() != this.level()) {
                this.discard();
            }
        }
    }

    private void dropAllPassengersAsItems() {
        if (level().isClientSide) return;
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof ItemEntity item) {
                ItemStack stack = item.getItem().copy();
                ItemEntity drop = new ItemEntity(level(), this.getX(), this.getY(), this.getZ(), stack);
                drop.setPickUpDelay(10);
                level().addFreshEntity(drop);
            }
            passenger.discard();
        }
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            ItemEntity drop = new ItemEntity(level(), this.getX(), this.getY(), this.getZ(), carried.copy());
            drop.setPickUpDelay(10);
            level().addFreshEntity(drop);
            setCarried(ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (ownerUUID != null) compound.putUUID("Owner", ownerUUID);
        compound.putDouble("AnchorX", anchor.x);
        compound.putDouble("AnchorY", anchor.y);
        compound.putDouble("AnchorZ", anchor.z);
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) compound.put("Carried", carried.save(new CompoundTag()));
        compound.putBoolean("Returning", isReturning());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) ownerUUID = compound.getUUID("Owner");
        anchor = new Vec3(compound.getDouble("AnchorX"), compound.getDouble("AnchorY"), compound.getDouble("AnchorZ"));
        if (compound.contains("Carried")) {
            ItemStack s = ItemStack.of(compound.getCompound("Carried"));
            setCarried(s);
        }
        if (compound.contains("Returning")) setReturning(compound.getBoolean("Returning"));
        if (ownerUUID != null && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var e = sl.getEntity(ownerUUID);
            if (e instanceof Player p) owner = p;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return super.getAddEntityPacket();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
