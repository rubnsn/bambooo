package ruby.bamboo.entity.arrow;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 爆発矢の時限爆発 (旧 TimerBomb の簡略版)。
 * <p>
 * 旧仕様: 親モブの「命中時から爆発までに減った HP 量」を威力とし、
 * 3×3×3 範囲の LivingEntity へダメージ+ノックバック。ブロック破壊なし。
 * <p>
 * EntityType を増やさないため、ExplodeArrowEntity が命中時に {@link #attach}
 * を呼び、ServerTickEvent 経由で毎 tick カウントダウンする。
 * NBT 非保存 (旧 readEntityFromNBT/writeEntityToNBT 空実装相当) のため
 * サーバー再起動で自然破棄される。旧挙動と同等。
 */
public final class TimerBomb {

    /** タイマー切れ後の猶予 (旧 FUSE_TIMER=40tick) */
    private static final int FUSE_TICKS = 40;

    private static final List<TimerBomb> ACTIVE = new java.util.ArrayList<>();

    private final ServerLevel level;
    private final LivingEntity target;
    private int timer;
    /** 命中時の HP (威力計算の基準) */
    private float startHp;
    /** 爆発威力 (fuse 中に確定)。-1 は未確定 */
    private float pow = -1.0F;

    private boolean removed;

    private TimerBomb(ServerLevel level, LivingEntity target, int delay) {
        this.level = level;
        this.target = target;
        this.timer = delay;
        this.startHp = target.getHealth();
    }

    /**
     * 対象モブへ時限爆発を取り付ける。
     *
     * @param delay 遅延 tick 数 (旧 timer = 200×power)
     */
    public static void attach(ServerLevel level, LivingEntity target, int delay) {
        synchronized (ACTIVE) {
            ACTIVE.add(new TimerBomb(level, target, delay));
        }
    }

    /**
     * 全 TimerBomb を 1tick 進める。Forge の ServerTickEvent から呼ばれる。
     */
    public static void tickAll() {
        synchronized (ACTIVE) {
            for (int i = ACTIVE.size() - 1; i >= 0; i--) {
                TimerBomb bomb = ACTIVE.get(i);
                bomb.tick();
                if (bomb.removed) {
                    ACTIVE.remove(i);
                }
            }
        }
    }

    private void tick() {
        if (!target.isAlive()) {
            // 親が死んだ → 全 HP 分の威力 (旧 pow=startHP)
            this.pow = startHp;
            this.playFuseSound();
            this.timer = Math.min(this.timer, -1);
        }

        // タイマー切れ → 減った HP 分の威力 (旧 timer<0)
        if (--this.timer < 0 && this.pow < 0.0F && this.timer >= -FUSE_TICKS) {
            this.pow = Math.max(0.0F, startHp - target.getHealth());
            this.playFuseSound();
        }

        if (target.isAlive()) {
            this.level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    target.getX(), target.getY() + target.getEyeHeight(), target.getZ(),
                    1, 0.05, 0.05, 0.05, 0);
        } else if ((this.timer & 7) == 0 && this.timer > -FUSE_TICKS) {
            Vec3 pos = new Vec3(target.getX(), target.getY(), target.getZ());
            this.level.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0);
        }

        // fuse 終了 → 爆発
        if (this.timer < -FUSE_TICKS) {
            this.explode(this.pow >= 0.0F ? this.pow : startHp);
            this.removed = true;
        }
    }

    private void playFuseSound() {
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /**
     * 旧 explode(pow) 相当。3×3×3 範囲の LivingEntity へダメージ+ノックバック。
     * ブロックは壊さない。
     */
    private void explode(float pow) {
        AABB box = target.getBoundingBox().inflate(3.0D);
        List<Entity> list = level.getEntities(target, box, e -> e instanceof LivingEntity);
        Vec3 center = target.position();
        DamageSource source = level.damageSources().generic();

        for (Entity e : list) {
            double distance = e.distanceTo(target) / 6.0D;

            double offsetX = e.getX() - center.x;
            double offsetY = (e.getY() + e.getEyeHeight()) - center.y;
            double offsetZ = e.getZ() - center.z;
            double offsetSqrt = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);

            if (offsetSqrt != 0.0D) {
                offsetX /= offsetSqrt;
                offsetY /= offsetSqrt;
                offsetZ /= offsetSqrt;

                double density = net.minecraft.world.level.Explosion.getSeenPercent(center, e);
                double d10 = (1.0D - distance) * density;
                // 旧式: ((d10²+d10)/2 × 8 × pow + 1)
                double dmg = ((d10 * d10 + d10) / 2.0D) * 8.0D * pow + 1.0D;
                e.hurt(source, (float) dmg);

                e.setDeltaMovement(e.getDeltaMovement()
                        .add(offsetX * d10, offsetY * d10, offsetZ * d10));
            }
        }

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4.0F,
                (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        for (int i = 0; i < 4; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION,
                    center.x + (level.random.nextInt(6) - 3),
                    center.y + level.random.nextInt(2) - 1,
                    center.z + (level.random.nextInt(6) - 3),
                    1, 0, 0, 0, 0);
        }
    }
}
