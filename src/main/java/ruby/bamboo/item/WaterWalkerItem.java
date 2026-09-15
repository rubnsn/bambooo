package ruby.bamboo.item;

import net.minecraft.ChatFormatting;
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
 * インベントリ所持中、スニーク・滑空・騎乗・泳ぎ中でなければ水面を歩行できる。スニークは解除 (潜水可能)。
 * 水面ではジャンプで飛び出す (sakura 踏襲)。水中呼吸・水中採掘中は発動しない。
 * <p>
 * 1.21: {@code appendHoverText} のシグネチャ変更 ({@code Item.TooltipContext} 追加) のみ。
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.water_walker").withStyle(ChatFormatting.AQUA));
    }
}
