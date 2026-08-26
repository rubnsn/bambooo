package ruby.bamboo.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class FlashJump extends EnchantmentBase {
    public FlashJump(Rarity rarity, EnchantmentCategory category, EquipmentSlot... slots) {
        super(rarity, category, slots);
    }
    @Override public int getMinCost(int level) { return 20; }
    @Override public int getMaxCost(int level) { return 50; }
    @Override public int getMaxLevel() { return 1; }
}
