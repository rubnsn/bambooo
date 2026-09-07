package ruby.bamboo.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ウォーターウォーカー (sakura WaterWalker の 1.20.1 移植。登録名の typo water_warker を修正)。
 * <p>
 * インベントリ所持中、非スニーク時に水上を歩行できる。スニークは解除 (水ポチャ)。
 * 水上ではジャンプできない (sakura 通り)。滑空・水泳・騎乗中は迂回する。
 */
public class WaterWalkerItem extends Item implements Accessory {

    public WaterWalkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void playerPostTick(Player player, ItemStack stack) {
        if (player.isCrouching() || player.isFallFlying() || player.isSpectator() || player.isPassenger()
                || player.isSwimming()) {
            return;
        }
        Level level = player.level();
        Vec3 motion = player.getDeltaMovement();
        BlockPos feet = BlockPos.containing(player.getX(), Math.floor(player.getY()), player.getZ());
        BlockPos below = BlockPos.containing(player.getX(), Math.ceil(player.getY() + motion.y) - 0.0625D,
                player.getZ());
        if (level.getBlockState(feet).isAir() && level.getFluidState(below).is(FluidTags.WATER)) {
            player.setDeltaMovement(motion.x, 0.0D, motion.z);
            player.setOnGround(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.water_walker").withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
