package ruby.bamboo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 釣りエサ (汎用・消耗)。仕様書 §2: バイトパワー 6。
 *
 * <p>1.21.1 NeoForge: appendHoverText は TooltipContext 形式。
 */
public class FishingBaitItem extends BambooItem {

    public static final int BITE_POWER = 6;

    public FishingBaitItem(Properties properties) {
        super(properties);
    }

    public int getBitePower() {
        return BITE_POWER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.bait_consumable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
