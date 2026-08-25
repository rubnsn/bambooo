package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class Pickpocket extends EnchantmentBase {
    public Pickpocket(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 10 + (level - 1) * 15; }
    @Override public int getMaxCost(int level) { return getMinCost(level) + 15; }
    @Override public int getMaxLevel() { return 5; }
}
