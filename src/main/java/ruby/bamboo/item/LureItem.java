package ruby.bamboo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ルアー (耐久制)。仕様書 §2: バイトパワー 2 / 4 / 6。
 */
public class LureItem extends BambooItem {

    private final int bitePower;

    public LureItem(Properties properties, int bitePower) {
        super(properties);
        this.bitePower = bitePower;
    }

    public int getBitePower() {
        return bitePower;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.bait_power", bitePower)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bamboomod.bait_lure")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (stack.isDamageableItem()) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            tooltip.add(Component.translatable("tooltip.bamboomod.lure_durability", remaining, stack.getMaxDamage())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
