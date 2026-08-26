package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class SnipeThrow extends EnchantmentBase {
    public SnipeThrow(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 10 + (level - 1) * 20; }
    @Override public int getMaxCost(int level) { return getMinCost(level) + 20; }
    @Override public int getMaxLevel() { return 3; }
}
