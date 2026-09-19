package ruby.bamboo.entity;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.item.MonsterFigureItem;

/**
 * 設置されたモンスターフィギュア (docs/port-spec-capsule-ball.md §10)。
 * 見た目・サイズ・角度は EntityData で全クライアントへ同期される。
 * AIなし・押不可。重力で着地する。攻撃で破壊されアイテムに戻る。
 */
public class FigureEntity extends Entity {

    private static final EntityDataAccessor<String> DATA_ID = SynchedEntityData.defineId(
            FigureEntity.class, EntityDataSerializers.STRING);
    /** 個体の saveWithoutId 全文 (+id)。描画ダミーの再構成用 */
    private static final EntityDataAccessor<CompoundTag> DATA_ENTITY = SynchedEntityData.defineId(
            FigureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 実物比スケール */
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(
            FigureEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(
            FigureEntity.class, EntityDataSerializers.FLOAT);
    /** 可動部角度 {part:[x,y,z]} (度) */
    private static final EntityDataAccessor<CompoundTag> DATA_POSE = SynchedEntityData.defineId(
            FigureEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public FigureEntity(EntityType<? extends FigureEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isNoGravity()) return;
        // 置き物らしく重力で着地させる (空中設置の浮遊防止)
        Vec3 delta = getDeltaMovement();
        double vy = onGround() ? 0.0D : Math.max(delta.y - 0.08D, -3.0D);
        setDeltaMovement(delta.x * 0.9D, vy, delta.z * 0.9D);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** フィギュアスタックから実体化する。 */
    @Nullable
    public static FigureEntity spawnFromFigure(ServerLevel level, Vec3 feetPos, float yaw,
            ItemStack figureStack) {
        MonsterFigureItem.FigureData figure = MonsterFigureItem.read(figureStack);
        if (figure == null) return null;
        FigureEntity entity = new FigureEntity(BambooEntities.FIGURE.get(), level);
        entity.entityData.set(DATA_ID, figure.entityId());
        entity.entityData.set(DATA_ENTITY, figure.data().copy());
        entity.entityData.set(DATA_SCALE, figure.scale());
        entity.entityData.set(DATA_YAW, yaw);
        entity.entityData.set(DATA_POSE, figure.pose().copy());
        entity.moveTo(feetPos.x, feetPos.y, feetPos.z, yaw, 0.0F);
        entity.refreshDimensions();
        if (!level.addFreshEntity(entity)) return null;
        return entity;
    }

    public String getFigureId() {
        try {
            return this.entityData.get(DATA_ID);
        } catch (Exception e) {
            return "";
        }
    }

    public CompoundTag getFigureData() {
        try {
            return this.entityData.get(DATA_ENTITY).copy();
        } catch (Exception e) {
            return new CompoundTag();
        }
    }

    public float getFigureScale() {
        try {
            float s = this.entityData.get(DATA_SCALE);
            return s > 0.0F ? s : 1.0F;
        } catch (Exception e) {
            return 1.0F;
        }
    }

    public float getFigureYaw() {
        try {
            return this.entityData.get(DATA_YAW);
        } catch (Exception e) {
            return 0.0F;
        }
    }

    public CompoundTag getFigurePose() {
        try {
            return this.entityData.get(DATA_POSE).copy();
        } catch (Exception e) {
            return new CompoundTag();
        }
    }

    public void setFigureScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
    }

    public void setFigureYaw(float yaw) {
        this.entityData.set(DATA_YAW, yaw);
    }

    public void setFigurePose(CompoundTag pose) {
        this.entityData.set(DATA_POSE, pose.copy());
    }

    /** 現在の状態 (サイズ・角度・ポーズ込み) をフィギュアスタックへ戻す。 */
    public ItemStack toStack() {
        ItemStack stack = MonsterFigureItem.create(getFigureId(), getFigureData(),
                getFigureScale(), getFigureYaw());
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().put(MonsterFigureItem.TAG_POSE, getFigurePose());
        }
        return stack;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ID, "");
        this.entityData.define(DATA_ENTITY, new CompoundTag());
        this.entityData.define(DATA_SCALE, 1.0F);
        this.entityData.define(DATA_YAW, 0.0F);
        this.entityData.define(DATA_POSE, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_ID, tag.getString("FigureId"));
        if (tag.contains("FigureData")) {
            this.entityData.set(DATA_ENTITY, tag.getCompound("FigureData").copy());
        }
        if (tag.contains("FigureScale")) {
            this.entityData.set(DATA_SCALE, tag.getFloat("FigureScale"));
        }
        if (tag.contains("FigureYaw")) {
            this.entityData.set(DATA_YAW, tag.getFloat("FigureYaw"));
        }
        if (tag.contains("FigurePose")) {
            this.entityData.set(DATA_POSE, tag.getCompound("FigurePose").copy());
        }
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("FigureId", getFigureId());
        tag.put("FigureData", getFigureData());
        tag.putFloat("FigureScale", getFigureScale());
        tag.putFloat("FigureYaw", getFigureYaw());
        tag.put("FigurePose", getFigurePose());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions base = EntityDimensions.fixed(0.5F, 0.5F);
        try {
            var opt = EntityType.byString(getFigureId());
            if (opt.isPresent()) base = opt.get().getDimensions();
        } catch (Exception e) {
        }
        return base.scale(getFigureScale());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            this.spawnAtLocation(toStack());
            this.discard();
        }
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return toStack();
    }
}
