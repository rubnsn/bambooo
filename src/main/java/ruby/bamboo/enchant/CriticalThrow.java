package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class CriticalThrow extends EnchantmentBase {
    public CriticalThrow(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 5 + (level - 1) * 18; }
    @Override public int getMaxCost(int level) { return getMinCost(level) + 17; }
    @Override public int getMaxLevel() { return 5; }
}
