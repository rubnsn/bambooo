package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class InfinityThrow extends EnchantmentBase {
    public InfinityThrow(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 25; }
    @Override public int getMaxCost(int level) { return 55; }
    @Override public int getMaxLevel() { return 1; }
}
