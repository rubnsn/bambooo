package ruby.bamboo.item.arrow;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.arrow.BambooArrowEntity;

/**
 * 竹矢 (旧 BambooArrow)。
 * <p>
 * 弓で発射時、チャージ量に応じて最大5本の連射 (barrage)。
 * クリエイティブ飛行中は 12本 + power 1.0 (旧仕様)。
 */
public class BambooArrowItem extends ArrowBase {

    /** 連射上限 (旧 limit = 5) */
    public static final int BARRAGE_LIMIT = 5;

    public BambooArrowItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 弓発射時の追従本数計算 (旧 createArrowIn の barrage 計算相当)。
     *
     * @param chargeFrame チャージ tick
     * @param ownedCount  インベントリ内の所持数
     */
    public static int calcBarrage(int chargeFrame, boolean creativeFlying, int ownedCount) {
        int count = Math.min(chargeFrame / 10, BARRAGE_LIMIT);
        if (creativeFlying) {
            return 12; // creative 飛行: 固定12本
        }
        count = Math.min(count, ownedCount);
        return Math.max(1, count);
    }

    @Override
    protected AbstractArrow createArrow(Level level, LivingEntity shooter, float velocity) {
        return new BambooArrowEntity(level, shooter);
    }
}
