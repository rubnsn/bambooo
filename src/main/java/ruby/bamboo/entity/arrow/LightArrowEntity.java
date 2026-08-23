package ruby.bamboo.entity.arrow;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.core.init.BambooItems;

/**
 * 軽量矢 (旧 EntityLightArrow)。
 * <p>
 * 重力半減 (旧 motionUpdate の y 減衰 ×0.5 相当)。
 * AbstractArrow の tick 内では重力が固定のため、tick 前後で y 速度を補正する。
 */
public class LightArrowEntity extends AbstractArrow {

    public LightArrowEntity(EntityType<? extends LightArrowEntity> type, Level level) {
        super(type, level);
    }

    public LightArrowEntity(EntityType<? extends LightArrowEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    public LightArrowEntity(Level level, LivingEntity shooter) {
        this(BambooEntities.LIGHT_ARROW.get(), shooter, level);
    }

    @Override
    public void tick() {
        // AbstractArrow.tick 内の重力適用 (dy -= 0.05) を半減させる:
        // tick 前に y 速度へ +0.025 加算しておき、tick 後の実効重力を 0.025 にする
        if (!this.inGround && !this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.025D, 0.0D));
        }
        super.tick();
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(BambooItems.LIGHT_ARROW.get());
    }
}
