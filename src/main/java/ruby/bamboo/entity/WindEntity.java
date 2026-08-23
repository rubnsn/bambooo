package ruby.bamboo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 風エンティティ (旧 Wind の 1.20.1 移植)。
 * <p>
 * 旧仕様: EntityThrowable 継承、setSize(5,5)、寿命5tick、偶数tickごとに周囲ブロック走査で
 * LEAVES/VINE/DoublePlant を破壊。サーバでドロップ+空気化、クライアントで SakuraPetal 演出。
 * <p>
 * 1.20.1ではロジックをサーバ完結にし、演出は cherry_leaves パーティクル送信に置換。
 * エンティティ衝突は無効(onImpact空実装相当)。
 */
public class WindEntity extends Entity {

    private static final int MAX_AGE = 5;

    public WindEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();

        // 移動
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.position().add(motion));
        // 軽い空気抵抗のみ(旧 EntityThrowable既定に近い)。重力はほぼ無し
        this.setDeltaMovement(motion.scale(0.99));

        // 寿命
        if (this.tickCount > MAX_AGE) {
            this.discard();
            return;
        }

        // 偶数tickごと(2,4)で走査 — 旧 (age & 1)==0 を踏襲
        if ((this.tickCount & 1) == 0) {
            checkBlockCollision();
        }
    }

    /**
     * 旧 setHeadingFromThrower 相当。プレイヤーの視線から初速を与える。
     *
     * @param shooter    発射者(揺らぎ基準には使わないが将来拡張用)
     * @param pitch      xRot
     * @param yaw        yRot
     * @param roll       補正角(未使用、旧0.0F)
     * @param velocity   初速 1.5F
     * @param inaccuracy 分散 1.0F
     */
    public void shootFromRotation(net.minecraft.world.entity.Entity shooter, float pitch, float yaw, float roll, float velocity, float inaccuracy) {
        float f = -Mth.sin(yaw * ((float) Math.PI / 180F)) * Mth.cos(pitch * ((float) Math.PI / 180F));
        float f1 = -Mth.sin((pitch + roll) * ((float) Math.PI / 180F));
        float f2 = Mth.cos(yaw * ((float) Math.PI / 180F)) * Mth.cos(pitch * ((float) Math.PI / 180F));
        this.shoot(f, f1, f2, velocity, inaccuracy);
    }

    public void shoot(double dx, double dy, double dz, float velocity, float inaccuracy) {
        Vec3 vec = (new Vec3(dx, dy, dz)).normalize()
                .add(this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * (double) inaccuracy))
                .scale(velocity);
        this.setDeltaMovement(vec);
        double d0 = vec.horizontalDistance();
        this.setYRot((float) (Mth.atan2(vec.x, vec.z) * (double) (180F / (float) Math.PI)));
        this.setXRot((float) (Mth.atan2(vec.y, d0) * (double) (180F / (float) Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    private void checkBlockCollision() {
        // サーバ側のみ破壊処理。クライアントでは演出不要(サーバからパーティクル送信)
        if (this.level().isClientSide) {
            return;
        }
        AABB bb = this.getBoundingBox().inflate(-0.001D);
        int minX = Mth.floor(bb.minX);
        int minY = Mth.floor(bb.minY);
        int minZ = Mth.floor(bb.minZ);
        int maxX = Mth.floor(bb.maxX);
        int maxY = Mth.floor(bb.maxY);
        int maxZ = Mth.floor(bb.maxZ);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = this.level().getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    if (isRemove(state)) {
                        removeLeaves(mutable.immutable(), state);
                    }
                }
            }
        }
    }

    private boolean isRemove(BlockState state) {
        // 1.20.1では Material.LEAVES/VINE は Tag へ置換
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }
        // VINE は CLIMBABLE タグに含まれる(蔓系)。旧 VINE 対応
        if (state.is(BlockTags.CLIMBABLE)) {
            return true;
        }
        // 背の高い植物 (旧 BlockDoublePlant) — 1.20では DoublePlantBlock
        if (state.getBlock() instanceof DoublePlantBlock) {
            return true;
        }
        return false;
    }

    private void removeLeaves(BlockPos pos, BlockState state) {
        Level level = this.level();
        // サーバ側: ドロップ + 空気化 + パーティクル送信
        // クライアント分岐は廃止し、サーバ完結にする
        // 1) パーティクル (破壊前に送信すると位置が分かりやすい)
        if (level instanceof ServerLevel serverLevel) {
            // 桜色演出: CHERRY_LEAVES を 5 粒送信 (旧 SakuraPetal 相当)
            // 風の速度を少し引き継いで拡散させる
            Vec3 motion = this.getDeltaMovement();
            // パーティクル速度は 0 固定ではなく微速で漂わせる
            serverLevel.sendParticles(ParticleTypes.CHERRY_LEAVES,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5,
                    0.3, 0.3, 0.3,
                    0.02);
            // 追加で風の進行方向に少し流す (オプション: CHERRY_LEAVES は速度無視だが見た目は変わらない)
            // 旧 petal の motion 継承は ParticleTypes では再現できないため、位置拡散で代替
        }

        // 2) ドロップ + 破壊
        // level.destroyBlock は loot table に従ってドロップを生成する
        level.destroyBlock(pos, true);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // 寿命5tickで保存不要
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
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
