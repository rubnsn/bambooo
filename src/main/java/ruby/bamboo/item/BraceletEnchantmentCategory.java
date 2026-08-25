package ruby.bamboo.item;

import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 腕輪専用 EnchantmentCategory (HiganEnchantType.BRACELET 相当)。
 */
public final class BraceletEnchantmentCategory {
    public static final EnchantmentCategory BRACELET = EnchantmentCategory.create("bracelet", item -> item instanceof NinjaBraceletItem);

    private BraceletEnchantmentCategory() {}
}
