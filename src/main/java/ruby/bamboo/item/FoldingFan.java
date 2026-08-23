package ruby.bamboo.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ruby.bamboo.core.init.BambooEntities;
import ruby.bamboo.entity.WindEntity;

/**
 * 扇子 (旧 FoldingFan の 1.20.1 移植)。
 * <p>
 * 右クリックで {@link WindEntity} を発射し、耐久を1消費する。
 * 旧仕様: damageItem(1) → setHeadingFromThrower(1.5F,1.0F) → PASS返却。
 */
public class FoldingFan extends Item {

    public FoldingFan(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            WindEntity wind = BambooEntities.WIND.get().create(level);
            if (wind != null) {
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                // 眼の少し前に配置して自プレイヤーとの即時衝突を回避
                wind.moveTo(eye.x + look.x * 0.5, eye.y, eye.z + look.z * 0.5, player.getYRot(), player.getXRot());
                wind.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                level.addFreshEntity(wind);
            }
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }

        // 旧仕様は常に PASS。SUCCESS にすると腕振り等の挙動が変わるため PASS を維持。
        // hurtAndBreak は PASS でも正常に動作する。
        return InteractionResultHolder.pass(stack);
    }
}
