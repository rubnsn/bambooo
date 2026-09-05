package ruby.bamboo.entity.arrow;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;

/**
 * 竹矢 (旧 EntityBambooArrow)。
 * <p>
 * 連射 (barrage): 命中後も残数がある間、追従矢を spawn する。
 * 追従矢はダメージ×0.7、命中対象の無敵時間をリセットして連続ヒットさせる。
 */
public class BambooArrowEntity extends AbstractArrow {

    /** 残り追従本数 */
    private int barrage;
    /** Punch相当のノックバック量 (1.21: AbstractArrowのknockback廃止のため自前保持) */
    private int knockback;
    /** 追従矢の初速 (発射時の power を引き継ぐ) */
    private double followPower = 1.0D;

    public BambooArrowEntity(EntityType<? extends BambooArrowEntity> type, Level level) {
        super(type, level);
    }

    public BambooArrowEntity(EntityType<? extends BambooArrowEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level, new ItemStack(BambooItems.BAMBOO_ARROW.get()), null);
    }

    public BambooArrowEntity(Level level, LivingEntity shooter) {
        this(BambooEntities.BAMBOO_ARROW.get(), shooter, level);
    }

    public BambooArrowEntity setBarrage(int count, double power) {
        this.barrage = count;
        this.followPower = power;
        return this;
    }

    public int getBarrage() {
        return this.barrage;
    }

    @Override
    public void tick() {
        super.tick();

        // 追従矢 spawn (旧 postUpdate 相当)
        // 命中で即消滅させず、barrage が尽きるまで次々に撃ち出す
        if (!this.level().isClientSide && this.barrage > 0 && !this.inGround) {
            this.barrage--;
            BambooArrowEntity follow = new BambooArrowEntity(this.level(),
                    this.getOwner() instanceof LivingEntity living ? living : null);
            follow.copyPosition(this);
            follow.shoot(this.getDeltaMovement().x, this.getDeltaMovement().y, this.getDeltaMovement().z,
                    1.0F, 0.0F);
            follow.setCritArrow(this.isCritArrow());
            follow.setBaseDamage(this.getBaseDamage() * 0.7D);
            follow.setKnockback(this.getKnockback());
            follow.setRemainingFireTicks(this.isOnFire() ? 100 : 0);
            follow.pickup = Pickup.ALLOWED;
            follow.setNoGravity(this.isNoGravity());
            follow.setBarrage(0, this.followPower);
            this.level().addFreshEntity(follow);
            // 発射音 (旧 playSound 相当)
            this.playSound(SoundEvents.ARROW_SHOOT, 1.0F,
                    1.0F / (this.random.nextFloat() * 0.4F + 1.2F) + (float) this.followPower * 0.5F);
        }
    }

    /**
     * 無敵時間無視で連続ヒットさせる (旧 onEntityHit の hurtResistantTime=0 相当)。
     * AbstractArrow#onHitEntity のダメージ処理後に呼ばれる doPostHurtEffects で
     * invulnerableTime を 0 に戻す。
     */
    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.invulnerableTime = 0;
        if (this.knockback > 0) {
            double d0 = Math.max(0.0, 1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            net.minecraft.world.phys.Vec3 vec3 = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize()
                    .scale(this.knockback * 0.6 * d0);
            if (vec3.lengthSqr() > 0.0) {
                target.push(vec3.x, 0.1, vec3.z);
            }
        }
    }

    public int getKnockback() {
        return this.knockback;
    }

    public void setKnockback(int knockback) {
        this.knockback = knockback;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(BambooItems.BAMBOO_ARROW.get());
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(BambooItems.BAMBOO_ARROW.get());
    }
}
