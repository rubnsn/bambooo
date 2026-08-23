package ruby.bamboo.item.arrow;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.arrow.ExplodeArrowEntity;

/**
 * 爆発矢 (旧 ExplodeArrow)。
 * <p>
 * ダメージ 0.5。命中したモブに時限爆発 (TimerBomb) を取り付ける。
 */
public class ExplodeArrowItem extends ArrowBase {

    /** 基礎ダメージ (旧 setDamage(0.5)) */
    public static final double BASE_DAMAGE = 0.5D;

    public ExplodeArrowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected AbstractArrow createArrow(Level level, LivingEntity shooter, float velocity) {
        AbstractArrow arrow = new ExplodeArrowEntity(level, shooter);
        arrow.setBaseDamage(BASE_DAMAGE);
        return arrow;
    }
}
