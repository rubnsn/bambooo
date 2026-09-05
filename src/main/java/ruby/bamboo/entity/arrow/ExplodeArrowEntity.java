package ruby.bamboo.entity.arrow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;

/**
 * 爆発矢 (旧 EntityExplodeArrow)。
 * <p>
 * 命中したモブに TimerBomb を取り付ける (旧 onEntityHited 相当)。
 * timer = 200 × power、威力は「命中時から爆発までに減った HP 量」。
 */
public class ExplodeArrowEntity extends AbstractArrow {

    public ExplodeArrowEntity(EntityType<? extends ExplodeArrowEntity> type, Level level) {
        super(type, level);
    }

    public ExplodeArrowEntity(EntityType<? extends ExplodeArrowEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level, new ItemStack(BambooItems.EXPLODE_ARROW.get()), null);
    }

    public ExplodeArrowEntity(Level level, LivingEntity shooter) {
        this(BambooEntities.EXPLODE_ARROW.get(), shooter, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        // TimerBomb 取り付け (旧 onEntityHited 相当)
        if (!this.level().isClientSide && result.getEntity() instanceof LivingEntity living
                && this.level() instanceof ServerLevel serverLevel) {
            float power = (float) (this.getDeltaMovement().length() / 3.0D); // 初速からの概算 power
            TimerBomb.attach(serverLevel, living, (int) (200.0F * Math.min(1.0F, power)));
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(BambooItems.EXPLODE_ARROW.get());
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(BambooItems.EXPLODE_ARROW.get());
    }

    /** Punch相当のノックバック量 (1.21: AbstractArrowのknockback廃止のため自前保持) */
    private int knockback;

    public int getKnockback() {
        return this.knockback;
    }

    public void setKnockback(int knockback) {
        this.knockback = knockback;
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        if (this.knockback > 0) {
            double d0 = Math.max(0.0, 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            net.minecraft.world.phys.Vec3 vec3 = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize()
                    .scale(this.knockback * 0.6 * d0);
            if (vec3.lengthSqr() > 0.0) {
                target.push(vec3.x, 0.1, vec3.z);
            }
        }
    }
}
