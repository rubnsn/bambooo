package ruby.bamboo.item.arrow;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.arrow.TorchArrowEntity;

/**
 * 松明矢 (旧 TorchArrow)。
 * <p>
 * 常時クリティカル、10% 自然着火、着地で松明設置 (エンティティ側)。
 * ダメージ 0.25 (+Power エンチャントは BambooBow 側で適用)。
 */
public class TorchArrowItem extends ArrowBase {

    /** 基礎ダメージ (旧 setDamage(0.25)) */
    public static final double BASE_DAMAGE = 0.25D;

    public TorchArrowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected AbstractArrow createArrow(Level level, LivingEntity shooter, float velocity) {
        TorchArrowEntity arrow = new TorchArrowEntity(level, shooter);
        arrow.setBaseDamage(BASE_DAMAGE);
        arrow.setCritArrow(true); // 常時クリティカル (旧 getIsCritical=true)
        return arrow;
    }
}
