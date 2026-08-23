package ruby.bamboo.item.arrow;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.arrow.LightArrowEntity;

/**
 * 軽量矢 (旧 LightArrow)。
 * <p>
 * ダメージ 1.0、重力半減 (エンティティ側)。
 */
public class LightArrowItem extends ArrowBase {

    /** 基礎ダメージ (旧 setDamage(1)) */
    public static final double BASE_DAMAGE = 1.0D;

    public LightArrowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected AbstractArrow createArrow(Level level, LivingEntity shooter, float velocity) {
        LightArrowEntity arrow = new LightArrowEntity(level, shooter);
        arrow.setBaseDamage(BASE_DAMAGE);
        return arrow;
    }
}
