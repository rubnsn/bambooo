package ruby.bamboo.entity;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.BambooMod;

/**
 * 座布団 (旧 EntityZabuton + EntityThrowZabuton の1.20.1移植・統合)。
 * <p>
 * 旧仕様: 16色 Entity (1.0 x 2/16)、右クリックで座る (mount)、
 * スニーク右クリックで投擲 (EntityThrowZabuton)、命中した生物を座布団に乗せる。
 * 降車時の地面埋まり対策 (旧81837c6: setDead時に pasajeros を先に降ろす) を踏襲。
 * <p>
 * 1.20.1では単一Entityに統合。投擲物は初速付きでスポーンし、
 * 移動中に接触した生物を乗せる。設置物は永続 (Chair と違い乗客消失で消えない)。
 * <p>
 * 1.21 相違点:
 * <ul>
 * <li>{@code defineSynchedData(SynchedEntityData.Builder)} 形式 (§5)。</li>
 * <li>{@code getAddEntityPacket(ServerEntity)} 形式 (§5。追加データ不要のため super 委譲)。</li>
 * <li>{@code shouldRiderSit / getPassengersRidingOffset} は1.21で削除
 * (ChairEntity と同様に override しない。乗車位置は attachment point 既定)。</li>
 * <li>ドロップ・PickBlock のアイテム解決は {@code BuiltInRegistries} 参照
 * (登録は親が {@code BambooItems.ZABUTONS} へ配線)。</li>
 * </ul>
 */
public class ZabutonEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ZabutonEntity.class,
            EntityDataSerializers.INT);
    /** 投擲中の回転速度 (度/tick。旧 rollMotionY = rand20-60)。設置時は 0 */
    private static final EntityDataAccessor<Float> DATA_SPIN = SynchedEntityData.defineId(ZabutonEntity.class,
            EntityDataSerializers.FLOAT);

    /** 投げた本人 (投擲直後の自爆乗り防止用)。設置時は null */
    @Nullable
    private LivingEntity thrower;
    /** 投擲後の経過tick (旧 ticksInAir。投擲者への衝突猶予5tick用) */
    private int ticksInAir;

    public ZabutonEntity(EntityType<? extends ZabutonEntity> type, Level level) {
        super(type, level);
    }

    /**
     * 登録済み EntityType をレジストリから解決する。
     * 親が {@code BambooEntities.ZABUTON} ("zabuton") を登録後に有効になる。
     */
    @SuppressWarnings("unchecked")
    public static EntityType<ZabutonEntity> registryType() {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "zabuton"));
        return (EntityType<ZabutonEntity>) type;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, ZabutonColor.WHITE.ordinal());
        builder.define(DATA_SPIN, 0.0F);
    }

    public ZabutonColor getColor() {
        return ZabutonColor.byOrdinal(this.entityData.get(DATA_COLOR));
    }

    public void setColor(ZabutonColor color) {
        this.entityData.set(DATA_COLOR, color.ordinal());
    }

    public void setThrower(LivingEntity thrower) {
        this.thrower = thrower;
        this.ticksInAir = 0;
        // 旧 EntityThrowZabuton.entityInit: rollMotionY = rand(40)+20。空中で回転する
        this.entityData.set(DATA_SPIN, (float) this.level().random.nextInt(40) + 20.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.getY() < this.level().getMinBuildHeight() - 64) {
                this.discard();
                return;
            }
        }
        // 投擲中の回転はクライアント側の描画用にのみ適用する
        // (旧 EntityThrowZabuton.onUpdate: rotationYaw += rollMotionY)。
        // サーバ側で回すと追跡パケットの補間と干渉してカクつくため、
        // サーバは設置時の向きを維持する。着地・搭乗で停止する
        if (this.level().isClientSide) {
            float spin = this.entityData.get(DATA_SPIN);
            if (spin != 0.0F && !this.onGround() && this.getPassengers().isEmpty()) {
                this.yRotO = this.getYRot();
                this.setYRot(this.getYRot() + spin);
            }
        }
        // 搭乗中は静止 (旧は重力継続だったが座布団が沈むため静止させる)
        if (!this.getPassengers().isEmpty()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        // 旧 EntityZabuton.onUpdate の物理を踏襲 (移動→減衰の順)。
        // 0付近で丸めても重力は毎tick掛け直す (丸め後に重力を抜くと頂点で永久浮遊する)
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 motion = this.getDeltaMovement();
        double motionY = motion.y;
        if (Math.abs(motionY) < 0.005D) {
            motionY = 0.0D;
        }
        motionY = (motionY - 0.08D) * 0.9800000190734863D;
        this.setDeltaMovement(motion.x * 0.91D, motionY, motion.z * 0.91D);

        if (!this.level().isClientSide) {
            this.ticksInAir++;
            // 投擲物の衝突マウント (旧 EntityThrowZabuton.onImpact 相当)
            if (this.getDeltaMovement().lengthSqr() > 0.04D) {
                AABB scan = this.getBoundingBox().inflate(0.5D);
                List<LivingEntity> hit = this.level().getEntitiesOfClass(LivingEntity.class, scan,
                        target -> !target.isPassenger() && (target != this.thrower || this.ticksInAir >= 5));
                if (!hit.isEmpty()) {
                    hit.get(0).startRiding(this);
                    this.setDeltaMovement(Vec3.ZERO);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // 旧 interactFirst: スニーク無しで座る
        if (!this.level().isClientSide && this.getPassengers().isEmpty() && !player.isSecondaryUseActive()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        // プレイヤー・モブ問わず1名のみ (旧 mountEntity 相当)
        return this.getPassengers().isEmpty() && passenger instanceof LivingEntity;
    }

    // 1.21: shouldRiderSit / getPassengersRidingOffset は削除 (ChairEntity と同様)。
    // 乗車位置は attachment point 既定 (Entity 原点 = 座布団上面付近)。

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 旧 attackEntityFrom: プレイヤー攻撃でのみ破壊・ドロップ。それ以外は無効
        if (this.isRemoved() || this.level().isClientSide) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            // 旧81837c6: 破棄前に乗客を降ろす (降車時の地面埋まり対策)
            this.ejectPassengers();
            this.discard();
            ItemStack drop = getColorStack();
            if (!drop.isEmpty() && !player.getAbilities().instabuild) {
                this.spawnAtLocation(drop);
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return getColorStack();
    }

    /**
     * 自色のアイテムをレジストリから解決する。
     * 親が {@code BambooItems} に {@code zabuton_<色>} を登録後に有効になる。
     * 未登録時は EMPTY (破壊ドロップなし・PickBlock なしとして振る舞う)。
     */
    private ItemStack getColorStack() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
                "zabuton_" + this.getColor().registryName()));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Color", this.getColor().ordinal());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setColor(ZabutonColor.byOrdinal(tag.getInt("Color")));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return super.getAddEntityPacket(entity);
    }
}
