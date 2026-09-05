package ruby.bamboo.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.block.GinkgoLeaveBlock;
import ruby.bamboo.block.HinokiLeaveBlock;
import ruby.bamboo.block.MapleLeaveBlock;
import ruby.bamboo.block.SakuraLeaveBlock;

/**
 * 風エンティティ (旧 Wind の 1.20.1 移植)。
 * <p>
 * 旧仕様: EntityThrowable 継承、setSize(5,5)、寿命5tick、偶数tickごとに周囲ブロック走査で
 * LEAVES/VINE/DoublePlant を破壊。サーバでドロップ+空気化、クライアントで SakuraPetal 演出。
 * <p>
 * 1.20.1では旧同様にサーバ/クライアント両方で走査し、サーバは破壊、クライアントは白ベース×乗算の
 * PetalParticle (旧 SakuraPetal.setMotion 再現) を1粒生成。色は IBlockColorWrapper 相当の
 * 葉色(桜/カエデ/イチョウ/ヒノキ/広葉)else 旧Green(0x3F9E55)。速度は旧正規化風ベクトル×0.15-0.45。
 * エンティティ衝突は無効(onImpact空実装相当)。サイズは旧 setSize(5,5)=幅5そのまま
 * (EntityType 5.0Fは直径)。中心は eye+look*0.5 で貫通射出。
 */
public class WindEntity extends Entity {

    private static final int MAX_AGE = 5;

    public WindEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
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
        // 旧 Wind.java:56-63 同様にAABB全体を走査（+0.001/-0.001の死にコードは再現せず、inflate無しで旧 getAllInBox 相当）
        // 端まで含めるため inflate しない。サーバは破壊、クライアントはパーティクル生成の二重処理を再現
        AABB bb = this.getBoundingBox();
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
                        // サーバ/クライアントで分岐（旧removeLeavesのisRemote分岐を踏襲）
                        if (this.level().isClientSide) {
                            spawnWindParticle(mutable.immutable(), state);
                        } else {
                            // サーバはドロップ+空気化
                            this.level().destroyBlock(mutable.immutable(), true);
                            // クライアント側でパーティクルは別途生成されるためサーバ送信不要
                        }
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

    private void spawnWindParticle(BlockPos pos, BlockState state) {
        // クライアント側のみ: 白ベース×乗算のPetalParticleを風継承で1粒生成
        // 色は旧 IBlockColorWrapper 相当: 葉色が取れるブロックはその色、else 白 0xFFFFFF
        Level level = this.level();
        if (!level.isClientSide) {
            return;
        }
        // ペタル種別と色を解決
        int color = 0xFFFFFF;
        net.minecraft.core.particles.SimpleParticleType petalType = ruby.bamboo.core.init.BambooParticles.PETAL_1.get();
        if (state.getBlock() instanceof SakuraLeaveBlock) {
            color = SakuraLeaveBlock.PETAL_COLOR;
            petalType = ruby.bamboo.core.init.BambooParticles.PETAL_1.get();
        } else if (state.getBlock() instanceof MapleLeaveBlock) {
            color = MapleLeaveBlock.PETAL_COLOR;
            petalType = ruby.bamboo.core.init.BambooParticles.PETAL_2.get();
        } else if (state.getBlock() instanceof GinkgoLeaveBlock) {
            color = GinkgoLeaveBlock.PETAL_COLOR;
            petalType = ruby.bamboo.core.init.BambooParticles.PETAL_3.get();
        } else if (state.getBlock() instanceof HinokiLeaveBlock) {
            color = HinokiLeaveBlock.PETAL_COLOR;
            petalType = ruby.bamboo.core.init.BambooParticles.PETAL_1.get(); // 旧Green petal_1
        } else {
            // バニラ葉/vine/DoublePlant は旧Green (0x3F9E55, petal_1) と同じ緑で統一
            // 旧 Wind は 0xFFFFFF だったが、指示により旧Greenに合わせる
            color = HinokiLeaveBlock.PETAL_COLOR; // 0x3F9E55
            petalType = ruby.bamboo.core.init.BambooParticles.PETAL_1.get();
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        double r = ((color >> 16) & 0xff) / 255.0;
        double g = ((color >> 8) & 0xff) / 255.0;
        double b = (color & 0xff) / 255.0;

        // 旧 SakuraPetal.setMotion の風継承をPetalParticle側ThreadLocalで再現、1粒で負荷軽減
        Vec3 wind = this.getDeltaMovement();
        ruby.bamboo.client.particle.PetalParticle.pushWind(wind);
        level.addParticle(petalType, x, y, z, r, g, b);
        // 万一 Providerでremoveされなかった場合の保険
        ruby.bamboo.client.particle.PetalParticle.clearWind();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // 寿命5tickで保存不要
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity entity) {
        return super.getAddEntityPacket(entity);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
