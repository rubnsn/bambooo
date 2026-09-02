package ruby.bamboo.handler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.fishing.FishingEntry;
import ruby.bamboo.core.fishing.FishingManager;

/**
 * 釣れた魚 (バニラ魚 with NBT) のツールチップへランクを付与する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FishingTooltipHandler {

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!stack.hasTag()) return;
        if (!stack.getTag().contains(FishSize.TAG_KEY)) return;
        String tagVal = stack.getTag().getString(FishSize.TAG_KEY);
        FishSize size = FishSize.fromTag(tagVal);
        // 対応する FishingEntry を itemId で検索
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return;
        FishingEntry matched = null;
        for (FishingEntry e : FishingManager.getEntries()) {
            if (e.itemId.equals(key)) {
                matched = e;
                break;
            }
        }
        if (matched == null) {
            event.getToolTip().add(Component.translatable("tooltip.bamboomod.fish_size." + size.tagValue)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        String sizeNameKey = "tooltip.bamboomod.fish_size." + size.tagValue;
        ChatFormatting color = switch (size) {
            case BRONZE -> ChatFormatting.GRAY;
            case SILVER -> ChatFormatting.WHITE;
            case GOLD -> ChatFormatting.GOLD;
        };
        event.getToolTip().add(Component.translatable(sizeNameKey).withStyle(color));
    }
}
