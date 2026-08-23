package ruby.bamboo.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * 刀 (旧 CommonKatana の移植)。
 * <p>
 * 旧仕様:
 * <ul>
 * <li>通常剣ダメージを無効化し、{@link #getAttackDamage(LivingEntity)} の独自ダメージで攻撃
 * (旧 onLeftClickEntity 相当。1.20.1 に同フックが無いため {@code item/katana/KatanaAttackHandler} で代替)</li>
 * <li>攻撃時の耐久消費なし</li>
 * <li>右クリックで60tickクールダウン = カウンター受付時間 (CounterManager が判定)</li>
 * <li>撃破時 KatanaDropManager (バニラ loot_table 併用) 経由で特殊ドロップ抽選</li>
 * </ul>
 */
public class CommonKatana extends SwordItem {

    public CommonKatana(Tier tier, Item.Properties properties) {
        super(tier, 0, -2.4F, properties);
    }

    /** 通常攻撃力を無効化 (旧 getDamageVsEntity()=0。実ダメージは KatanaAttackHandler が与える) */
    @Override
    public float getDamage() {
        return 0F;
    }

    /**
     * 対象別の独自ダメージ (旧 CommonKatana#getDamageVsEntity の移植)。
     * <p>
     * dmg = 4 × 倍率 + 1:
     * Zombie/Creeper/Spider=9、Animal=13、Skeleton=7、Slime=1、Blaze=3.8、Golem=1.4、その他=5。
     * 旧実装に合わせ float のまま丸めない。
     */
    public static float getAttackDamage(LivingEntity target) {
        if (target == null) {
            return 4F;
        }

        float rate = 1F;
        if (target instanceof Zombie || target instanceof ZombifiedPiglin || target instanceof Creeper
                || target instanceof Spider) {
            rate = 2F;
        } else if (target instanceof Animal) {
            rate = 3F;
        } else if (target instanceof AbstractSkeleton) {
            rate = 1.5F;
        } else if (target instanceof Slime) {
            rate = 0F;
        } else if (target instanceof Blaze) {
            rate = 0.7F;
        } else if (target instanceof IronGolem || target instanceof SnowGolem) {
            rate = 0.1F;
        }
        return 4F * rate + 1F;
    }

    /** 攻撃時に耐久を消費しない (旧仕様。onLeftClickEntity に damageItem が無い) */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // カウンター受付: 60tickクールダウン設定 (旧 KatanaBase#onItemRightClick)
        player.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /** ドロップ率基底値 (特殊刀で上書き前提。現状は通常刀のみ) */
    public static float getDropRate() {
        return 0F;
    }
}
