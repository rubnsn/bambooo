package ruby.bamboo.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import ruby.bamboo.entity.ShurikenEntity;

/**
 * 手裏剣 (旧 Shuriken の 1.20.1 移植)。
 * <p>
 * Tierで攻撃力差 (stone 2.0 / iron 3.5 / diamond 5.0)。
 * 素手でも投擲可能 (velocity 0.8, inaccuracy 5.0, cooldown 25)。
 * 腕輪経由では NinjaBraceletItem が弾として検索・消費する。
 */
public class ShurikenItem extends Item {

    private final Tier tier;
    private final float attackDamage;

    public ShurikenItem(Tier tier, Properties properties, float attackDamage) {
        super(properties);
        this.tier = tier;
        this.attackDamage = attackDamage;
    }

    public Tier getTier() {
        return tier;
    }

    public float getAttackDamage(ItemStack stack) {
        return attackDamage;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // クリエイティブ以外は1消費
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ShurikenEntity entity = new ShurikenEntity(level, player);
            entity.setItemStack(stack.copyWithCount(1));
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.8F, 5.0F);
            level.addFreshEntity(entity);
            player.getCooldowns().addCooldown(this, 25);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
