package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class TripleThrow extends EnchantmentBase {
    public TripleThrow(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 25; }
    @Override public int getMaxCost(int level) { return 55; }
    @Override public int getMaxLevel() { return 1; }
    @Override protected boolean checkCompatibility(Enchantment other) {
        if (other instanceof DoubleThrow) return false;
        return super.checkCompatibility(other);
    }
}
