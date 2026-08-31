package ruby.bamboo.entity.companion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ラマ仲間 - 蔵付きで操作できるラマ。
 * バニラ Llama は AbstractChestedHorse を継承し、chest 対応済み。
 * 本クラスは制御可能化 + デスポーン無効 + 帰還後の追従補助。
 */
public class LlamaCompanionEntity extends Llama {

    @Nullable
    private UUID ownerUUID;

    private static final String TAG_OWNER = "CompanionOwner";

    public LlamaCompanionEntity(EntityType<? extends Llama> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // default chest
        this.setChest(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Llama.createAttributes();
    }

    // owner handling
    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID(TAG_OWNER, ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_OWNER)) ownerUUID = tag.getUUID(TAG_OWNER);
        // ensure chested after load
        this.setChest(true);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.setPersistenceRequired();
    }

    // riding control
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
                if (f <= 0.0F) f *= 0.25F;
                else f *= 0.7F;
                f1 *= 0.5F;

                // handle jump if needed (llama doesn't jump high)
                super.travel(new Vec3(f1, travelVector.y, f));
                return;
            }
        }
        super.travel(travelVector);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // リンゴやニンジン等の Use を騎乗より優先
        if (isAppleOrCarrot(stack)) {
            if (this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                this.heal(4.0F);
                this.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
                if (!this.level().isClientSide) {
                    ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                            this.getX(), this.getY() + 1.2, this.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // if already tamed and chest, allow riding without extra item
        if (!player.isShiftKeyDown() && this.isTamed() && this.getPassengers().isEmpty()) {
            if (!this.level().isClientSide) {
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // try tame if not tamed
        if (!this.isTamed()) {
            // auto tame for companion wish
            if (!this.level().isClientSide) {
                this.tameWithName(player);
                this.setOwnerUUID(player.getUUID());
                this.setTamed(true);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private static boolean isAppleOrCarrot(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.is(Items.CARROT) || stack.is(Items.GOLDEN_CARROT);
    }

    // ensure tamed on summon helper
    public void tameForPlayer(ServerPlayer player) {
        this.tameWithName(player);
        this.setOwnerUUID(player.getUUID());
        this.setTamed(true);
        this.setPersistenceRequired();
        this.setChest(true);
    }
}
