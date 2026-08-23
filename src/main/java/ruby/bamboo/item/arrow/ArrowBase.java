package ruby.bamboo.item.arrow;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 矢アイテム基底 (旧 ArrowBase + IBambooArrow の 1.20.1 移植)。
 * <p>
 * 右クリックで矢エンティティを直接投げる (竹槍)。power = 0.5、初速 = power×2 = 1.0。
 */
public abstract class ArrowBase extends Item {

    /** 素手投げの威力 (旧 power = 0.5F) */
    public static final float THROW_POWER = 0.5F;

    public ArrowBase(Item.Properties properties) {
        super(properties);
    }

    public AbstractArrow createArrowForBow(Level level, LivingEntity shooter, float velocity) {
        return createArrow(level, shooter, velocity);
    }

    /**
     * 弓発射時に呼ばれ、この種に対応する矢エンティティを生成する。
     * (旧 IBambooArrow#createArrowIn 相当。エンチャント適用は BambooBow 側)
     *
     * @param velocity 初速 (power × 2)
     */
    protected abstract AbstractArrow createArrow(Level level, LivingEntity shooter, float velocity);

    /**
     * 竹槍として直接投げる (旧 onItemRightClick 相当)。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        float power = THROW_POWER;
        AbstractArrow arrow = createArrow(level, player, power * 2.0F);
        arrow.setOwner(player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 2.0F, 1.0F);

        if (!level.isClientSide) {
            level.addFreshEntity(arrow);
            arrow.playSound(SoundEvents.ARROW_SHOOT, 1.0F,
                    1.0F / (player.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        } else {
            // クリエイティブ: 回収不可 + 寿命短縮 (旧 setMaxAge(60)/setNoPick 相当)
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
