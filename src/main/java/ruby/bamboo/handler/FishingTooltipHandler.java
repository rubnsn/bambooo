package ruby.bamboo.handler;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.fishing.FishingEntry;
import ruby.bamboo.core.fishing.FishingManager;

/**
 * 釣れた魚 (バニラ魚 with CUSTOM_DATA) のツールチップへランクを付与する。
 *
 * <p>1.21.1 NeoForge: GAME bus + クライアント限定 (MiniatureRenderManager の先例)、
 * hasTag/getTag 撤去のため DataComponents.CUSTOM_DATA 経由で読む。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FishingTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(FishSize.TAG_KEY)) return;
        String tagVal = tag.getString(FishSize.TAG_KEY);
        FishSize size = FishSize.fromTag(tagVal);
        // 対応する FishingEntry を itemId で検索
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
