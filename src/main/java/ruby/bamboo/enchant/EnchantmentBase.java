package ruby.bamboo.enchant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;

abstract class EnchantmentBase extends Enchantment {

    protected EnchantmentBase(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }

    @Override
    public Component getFullname(int level) {
        if (level < this.getMaxLevel()) {
            return super.getFullname(level);
        }
        Component comp = Component.translatable(this.getDescriptionId() + "_ex");
        if (this.isCurse()) {
            comp = comp.copy().withStyle(ChatFormatting.RED);
        } else {
            comp = comp.copy().withStyle(ChatFormatting.GRAY);
        }
        if (this.getMaxLevel() < level) {
            comp = comp.copy().append("★");
        }
        return comp;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return false;
    }
}
