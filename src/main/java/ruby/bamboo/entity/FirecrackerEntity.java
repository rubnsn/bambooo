package ruby.bamboo.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.item.FirecrackerItem;

/**
 * かんしゃく玉エンティティ (旧 EntityFirecracker の 1.20.1 移植)。
 * <p>
 * 旧版は {@code EntityThrowable} で着弾即爆発だったが、本移植では時限導火線式に変更:
 * 投げた時点で着火済みで、S=20tick / M・L=60tick 後に爆発する。
 * 着弾では爆発せず、当たったブロックの硬さ ({@code destroySpeed}) に応じて
 * バウンドする (石など硬い=高反発、土・草・砂など柔らかい=低反発ですぐ止まる)。
 * 球体として転がる想定で、ワールド描画はクロススプライト+回転。
 * <p>
 * S: 威力2.0・ブロック破壊なし (エフェクトと音だけ)。
 * M: 威力4.0 (TNT相当)・通常破壊。
 * L: 威力4.0・耐爆性無視 (破壊不能ブロックは壊さない)。
 * sticky系はブロック・エンティティに固着する。
 */
public class FirecrackerEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(FirecrackerEntity.class, EntityDataSerializers.INT);
    /** 物理停止中 (地面静止 / ブロック固着 / エンティティ固着)。同期され、両側で物理を止める。 */
    private static final EntityDataAccessor<Boolean> DATA_PINNED = SynchedEntityData.defineId(FirecrackerEntity.class, EntityDataSerializers.BOOLEAN);

    private int fuse;
    private boolean lastGlow;

    @Nullable
    private BlockPos stuckPos;
    @Nullable
    private UUID stuckEntityUUID;
    private Vec3 stickOffset = Vec3.ZERO;

    public FirecrackerEntity(EntityType<? extends FirecrackerEntity> type, Level level) {
        super(type, level);
    }

    /** 手投げ用。投げた時点で着火済み (fuse は Type の既定値)。 */
    public FirecrackerEntity(Level level, LivingEntity owner, ItemStack stack) {
        this(BambooEntities.FIRECRACKER.get(), level);
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        this.setItem(stack.copyWithCount(1));
        this.setFirecrackerType(FirecrackerItem.Type.fromStack(stack));
    }

    /** ディスペンサー用。 */
    public FirecrackerEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(BambooEntities.FIRECRACKER.get(), level);
        this.setPos(x, y, z);
        this.setItem(stack.copyWithCount(1));
        this.setFirecrackerType(FirecrackerItem.Type.fromStack(stack));
    }

    public void setFirecrackerType(FirecrackerItem.Type type) {
        this.entityData.set(DATA_TYPE, type.id);
        this.fuse = type.fuseTicks;
    }

    public FirecrackerItem.Type getFirecrackerType() {
        try {
            return FirecrackerItem.Type.fromId(this.entityData.get(DATA_TYPE));
        } catch (Exception e) {
            return FirecrackerItem.Type.S;
        }
    }

    private void setPinned(boolean pinned) {
        this.entityData.set(DATA_PINNED, pinned);
    }

    private boolean isPinned() {
        try {
            return this.entityData.get(DATA_PINNED);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TYPE, FirecrackerItem.Type.S.id);
        this.entityData.define(DATA_PINNED, false);
    }

    @Override
    public void tick() {
        Level level = level();
        if (!level.isClientSide) {
            // エンティティ固着中は追従する
            if (stuckEntityUUID != null && level instanceof ServerLevel serverLevel) {
                Entity target = serverLevel.getEntity(stuckEntityUUID);
                if (target == null || target.isRemoved() || !target.isAlive()) {
                    // 対象が消えたら剥がれて落下再開 (導火線は継続)
                    stuckEntityUUID = null;
                    setPinned(false);
                } else {
                    this.setPos(target.getX() + stickOffset.x, target.getY() + stickOffset.y, target.getZ() + stickOffset.z);
                }
            }
            if (fuse > 0) {
                fuse--;
            }
            // M/L系は10tickごとに白く(発光)したり戻したりして着火済みを示す
            FirecrackerItem.Type type = getFirecrackerType();
            if (type.breaksBlocks) {
                boolean glow = (fuse / 10) % 2 == 0;
                if (glow != lastGlow) {
                    lastGlow = glow;
                    this.setGlowingTag(glow);
                }
            }
            if (fuse <= 0) {
                explode();
                return;
            }
            if (this.tickCount > 6000) {
                this.discard();
                return;
            }
        } else {
            // クライアント: 着火済みの煙 (Sは薄め、M/Lは濃いめ)
            FirecrackerItem.Type type = getFirecrackerType();
            if (this.tickCount <= type.fuseTicks + 5) {
                double dx = (this.random.nextDouble() - 0.5D) * 0.15D;
                double dz = (this.random.nextDouble() - 0.5D) * 0.15D;
                if (type.breaksBlocks) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.25D, getZ(), dx, 0.06D, dz);
                } else {
                    level.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.25D, getZ(), dx, 0.05D, dz);
                }
            }
        }
        if (isPinned()) {
            // 停止中は物理を進めず、導火線のみ進行させる
            this.baseTick();
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        Level level = level();
        BlockPos pos = result.getBlockPos();
        BlockState state = level.getBlockState(pos);
        FirecrackerItem.Type type = getFirecrackerType();
        Vec3 motion = getDeltaMovement();

        // sticky系はその場に固着する (両側で同じ判定にして同期ずれを防ぐ)
        if (type.sticky) {
            stickToBlock(result);
            return;
        }

        Direction dir = result.getDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double intoSurface = motion.dot(normal);
        Vec3 newMotion;
        float hardness = state.getDestroySpeed(level, pos);
        if (intoSurface < 0) {
            float restitution = restitutionFor(hardness);
            float tangentialKeep = tangentialKeepFor(hardness);
            if (dir == Direction.UP) {
                // 地面: 転がり摩擦も掛ける (柔らかい土の上ではすぐ止まる)
                tangentialKeep *= groundFrictionFor(hardness);
            }
            Vec3 reflected = motion.subtract(normal.scale((1.0D + restitution) * intoSurface));
            double normalSpeed = reflected.dot(normal);
            Vec3 normalPart = normal.scale(normalSpeed);
            Vec3 tangentialPart = reflected.subtract(normalPart).scale(tangentialKeep);
            newMotion = normalPart.add(tangentialPart);
        } else {
            // めり込み等、面から離れる向きの場合は減速のみ
            newMotion = motion.scale(0.5D);
        }
        // 面の外へ少し戻して再衝突を防ぐ
        this.setPos(getX() + normal.x * 0.05D, getY() + normal.y * 0.05D, getZ() + normal.z * 0.05D);
        if (!level.isClientSide) {
            level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    state.getSoundType(level, pos, this).getHitSound(), SoundSource.BLOCKS,
                    0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        // 上面で十分減速したら静止 (球体として止まる)
        if (dir == Direction.UP && newMotion.length() < 0.3D) {
            this.setDeltaMovement(Vec3.ZERO);
            setPinned(true);
        } else {
            this.setDeltaMovement(newMotion);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        FirecrackerItem.Type type = getFirecrackerType();
        if (type.sticky) {
            // エンティティに固着する
            stuckEntityUUID = target.getUUID();
            stickOffset = this.position().subtract(target.position());
            this.setDeltaMovement(Vec3.ZERO);
            setPinned(true);
            if (!level().isClientSide) {
                level().playSound(null, getX(), getY(), getZ(),
                        SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            return;
        }
        // 非stickyは跳ね返って飛び続ける (衝撃ダメージなし、爆発は導火線待ち)
        Vec3 away = this.position().subtract(target.position());
        if (away.lengthSqr() < 1.0E-6D) {
            away = getDeltaMovement().scale(-1.0D);
        }
        away = away.normalize();
        Vec3 motion = getDeltaMovement();
        double speed = Math.max(0.4D, motion.length() * 0.4D);
        Vec3 deflected = new Vec3(away.x, Math.abs(away.y) * 0.5D + 0.2D, away.z).normalize().scale(speed);
        this.setDeltaMovement(deflected);
        if (!level().isClientSide) {
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.4F, 1.2F);
        }
    }

    private void stickToBlock(BlockHitResult result) {
        Direction dir = result.getDirection();
        Vec3 loc = result.getLocation();
        this.setPos(loc.x - dir.getStepX() * 0.05D, loc.y - dir.getStepY() * 0.05D, loc.z - dir.getStepZ() * 0.05D);
        this.stuckPos = result.getBlockPos().immutable();
        this.setDeltaMovement(Vec3.ZERO);
        setPinned(true);
        if (!level().isClientSide) {
            level().playSound(null, loc.x, loc.y, loc.z,
                    SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }

    /**
     * 反発係数。石など硬いブロックは高反発、土・草・砂など柔らかいブロックは低反発。
     * 硬さの目安: 石1.5 / 木2.0 / 土0.5 / 草0.6 / 砂0.5 / ガラス0.3 / 黒曜石50 / 未破壊-1。
     */
    private static float restitutionFor(float hardness) {
        if (hardness < 0.0F) {
            return 0.55F; // 破壊不能 (岩盤等): よく跳ねる
        }
        if (hardness >= 1.0F) {
            return 0.55F + Math.min(0.15F, hardness * 0.01F); // 石系: 高反発
        }
        return 0.12F; // 土・草・砂系: 低反発
    }

    /** 接線方向の速度維持率 (1.0=減衰なし)。柔らかい面は横滑りも殺す。 */
    private static float tangentialKeepFor(float hardness) {
        if (hardness < 0.0F || hardness >= 1.0F) {
            return 0.85F;
        }
        return 0.5F;
    }

    /** 地面の転がり摩擦 (毎tickの維持率)。土の上ではすぐ止まる。 */
    private static float groundFrictionFor(float hardness) {
        if (hardness < 0.0F || hardness >= 1.0F) {
            return 0.96F; // 硬い地面: よく転がる
        }
        return 0.7F; // 柔らかい地面: すぐ止まる
    }

    private void explode() {
        Level level = level();
        if (level.isClientSide) {
            return;
        }
        FirecrackerItem.Type type = getFirecrackerType();
        // 1.20.1 に tnt_explodes gamerule は存在しないため、
        // ブロック破壊可否は mobGriefing で制御する (TNT相当の扱い)。
        boolean griefing = ForgeEventFactory.getMobGriefingEvent(level, this);
        Level.ExplosionInteraction interaction = Level.ExplosionInteraction.NONE;
        if (type.breaksBlocks && !type.ignoreResistance && griefing) {
            interaction = Level.ExplosionInteraction.TNT;
        }
        // 音・エフェクト・対エンティティ威力はバニラ爆発 (TNTとほぼ同じ)
        level.explode(this, getX(), getY(), getZ(), type.power, false, interaction);
        if (type.ignoreResistance && type.breaksBlocks && griefing) {
            // L系: 耐爆性を無視して破壊 (破壊不能・液体は除く)。旧版の独自Explosion相当。
            destroyBlocksIgnoringResistance(getX(), getY(), getZ(), type.power);
        }
        this.discard();
    }

    /**
     * 旧 EntityFirecracker.createExplosion のレイキャスト破壊を移植したもの。
     * 威力減衰に耐爆性を加味しない (L系専用) が、硬さ-1 (破壊不能) と液体は壊さない。
     */
    private void destroyBlocksIgnoringResistance(double x, double y, double z, float power) {
        Level level = level();
        Set<BlockPos> targets = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    if (i == 0 || i == 15 || j == 0 || j == 15 || k == 0 || k == 15) {
                        double dx = i / 15.0D * 2.0D - 1.0D;
                        double dy = j / 15.0D * 2.0D - 1.0D;
                        double dz = k / 15.0D * 2.0D - 1.0D;
                        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        dx /= len;
                        dy /= len;
                        dz /= len;
                        float strength = power * (0.7F + level.random.nextFloat() * 0.6F);
                        double px = x;
                        double py = y;
                        double pz = z;
                        for (; strength > 0.0F; strength -= 0.3F * 0.75F) {
                            BlockPos pos = BlockPos.containing(px, py, pz);
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && state.getFluidState().isEmpty()) {
                                // 破壊不能は貫通 (旧版の continue 相当)、それ以外は破壊対象
                                if (state.getDestroySpeed(level, pos) >= 0.0F) {
                                    targets.add(pos.immutable());
                                }
                            }
                            px += dx * 0.3D;
                            py += dy * 0.3D;
                            pz += dz * 0.3D;
                        }
                    }
                }
            }
        }
        for (BlockPos pos : targets) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            level.destroyBlock(pos, true, this);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return BambooItems.FIRECRACKER_S.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FirecrackerType", this.entityData.get(DATA_TYPE));
        tag.putInt("Fuse", this.fuse);
        tag.putBoolean("Pinned", this.entityData.get(DATA_PINNED));
        if (stuckPos != null) {
            tag.putLong("StuckPos", stuckPos.asLong());
        }
        if (stuckEntityUUID != null) {
            tag.putUUID("StuckEntity", stuckEntityUUID);
            tag.putDouble("StickDX", stickOffset.x);
            tag.putDouble("StickDY", stickOffset.y);
            tag.putDouble("StickDZ", stickOffset.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FirecrackerType")) {
            this.entityData.set(DATA_TYPE, tag.getInt("FirecrackerType"));
        }
        if (tag.contains("Fuse")) {
            this.fuse = tag.getInt("Fuse");
        } else {
            this.fuse = getFirecrackerType().fuseTicks;
        }
        if (tag.contains("Pinned")) {
            this.entityData.set(DATA_PINNED, tag.getBoolean("Pinned"));
        }
        if (tag.contains("StuckPos")) {
            stuckPos = BlockPos.of(tag.getLong("StuckPos"));
        }
        if (tag.hasUUID("StuckEntity")) {
            stuckEntityUUID = tag.getUUID("StuckEntity");
            stickOffset = new Vec3(tag.getDouble("StickDX"), tag.getDouble("StickDY"), tag.getDouble("StickDZ"));
        }
    }
}
