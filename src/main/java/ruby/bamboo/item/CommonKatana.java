package ruby.bamboo.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import ruby.bamboo.entity.KaginawaHookEntity;
import ruby.bamboo.network.KaginawaStateManager;

/**
 * 刀 (旧 CommonKatana の移植 → 鈎縄統合版)。
 * <p>
 * 旧仕様:
 * <ul>
 * <li>通常剣ダメージを無効化し、{@link #getAttackDamage(LivingEntity)} の独自ダメージで攻撃
 * (旧 onLeftClickEntity 相当。1.20.1 に同フックが無いため {@code item/katana/KatanaAttackHandler} で代替)</li>
 * <li>攻撃時の耐久消費なし</li>
 * <li>右クリックで鈎縄フックを発射/回収 (旧カウンター60tickはOMIT)</li>
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
        ItemStack stack = player.getItemInHand(hand);
        // サーバ側で鈎縄トグル
        if (!level.isClientSide) {
            // 既存フックがあれば回収 (トグル)
            if (KaginawaStateManager.hasHook(player)) {
                var existing = KaginawaStateManager.getHook(player);
                if (existing != null) {
                    existing.discardWithCleanup();
                } else {
                    KaginawaStateManager.remove(player);
                }
                // 回収時は速度継承のため何もしない — HookEntity側で player velocity は保持される
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
                player.getCooldowns().addCooldown(this, 5);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            // 新規発射
            KaginawaHookEntity hook = new KaginawaHookEntity(level, player);
            // 眼の少し前から発射 (埋没防止)
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            hook.moveTo(eye.x + look.x * 0.5, eye.y + look.y * 0.5, eye.z + look.z * 0.5, player.getYRot(), player.getXRot());
            hook.shootFromPlayer(player, player.getXRot(), player.getYRot(), 1.8F, 0.5F);
            level.addFreshEntity(hook);
            KaginawaStateManager.put(player, hook);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.7F, 1.4F);
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** ドロップ率基底値 (特殊刀で上書き前提。現状は通常刀のみ) */
    public static float getDropRate() {
        return 0F;
    }
}
