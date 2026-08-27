package ruby.bamboo.core.init;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.enchant.CriticalThrow;
import ruby.bamboo.enchant.DoubleThrow;
import ruby.bamboo.enchant.EconomyBracelet;
import ruby.bamboo.enchant.FlameThrow;
import ruby.bamboo.enchant.InfinityThrow;
import ruby.bamboo.enchant.Pickpocket;
import ruby.bamboo.enchant.PoisonThrow;
import ruby.bamboo.enchant.PowerThrow;
import ruby.bamboo.enchant.QuickThrow;
import ruby.bamboo.enchant.SnipeThrow;
import ruby.bamboo.enchant.TripleThrow;
import ruby.bamboo.enchant.UnbreakingBracelet;
import ruby.bamboo.item.BraceletEnchantmentCategory;

/**
 * 腕輪エンチャント12種登録 (HiganEnchant 13種の 1.20.1 移植、flash_jump は腕輪所持能力へ移行)。
 */
public final class BambooEnchantments {

    // BambooMod.ENCHANTMENTS が SSOT。旧 wt では独自 DeferredRegister を作成していたが
    // bus 未登録でエンチャントが無登録になるため SSOT へエイリアスする。
    public static final net.minecraftforge.registries.DeferredRegister<Enchantment> ENCHANTMENTS = BambooMod.ENCHANTMENTS;

    public static final RegistryObject<Enchantment> QUICK_THROW = register("quick_throw",
            () -> new QuickThrow(Enchantment.Rarity.COMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> POWER_THROW = register("power_throw",
            () -> new PowerThrow(Enchantment.Rarity.COMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> CRITICAL_THROW = register("critical_throw",
            () -> new CriticalThrow(Enchantment.Rarity.COMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> POISON_THROW = register("poison_throw",
            () -> new PoisonThrow(Enchantment.Rarity.COMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> SNIPE_THROW = register("snipe_throw",
            () -> new SnipeThrow(Enchantment.Rarity.COMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> ECONOMY_BRACELET = register("economy_bracelet",
            () -> new EconomyBracelet(Enchantment.Rarity.UNCOMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> UNBREAKING_BRACELET = register("unbreaking_bracelet",
            () -> new UnbreakingBracelet(Enchantment.Rarity.UNCOMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> PICKPOCKET = register("pickpocket",
            () -> new Pickpocket(Enchantment.Rarity.UNCOMMON, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> DOUBLE_THROW = register("double_throw",
            () -> new DoubleThrow(Enchantment.Rarity.RARE, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> TRIPLE_THROW = register("triple_throw",
            () -> new TripleThrow(Enchantment.Rarity.RARE, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> FLAME_THROW = register("flame_throw",
            () -> new FlameThrow(Enchantment.Rarity.RARE, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));
    public static final RegistryObject<Enchantment> INFINITY_THROW = register("infinity_throw",
            () -> new InfinityThrow(Enchantment.Rarity.RARE, BraceletEnchantmentCategory.BRACELET, EquipmentSlot.MAINHAND));

    private static RegistryObject<Enchantment> register(String name, java.util.function.Supplier<? extends Enchantment> sup) {
        return ENCHANTMENTS.register(name, sup);
    }

    public static void init() {
        // クリエタブへエンチャ本を追加（最大レベルのみ、調整用。バニラの StoredEnchantments NBT流用）
        addBook(QUICK_THROW);
        addBook(POWER_THROW);
        addBook(CRITICAL_THROW);
        addBook(POISON_THROW);
        addBook(SNIPE_THROW);
        addBook(ECONOMY_BRACELET);
        addBook(UNBREAKING_BRACELET);
        addBook(PICKPOCKET);
        addBook(DOUBLE_THROW);
        addBook(TRIPLE_THROW);
        addBook(FLAME_THROW);
        addBook(INFINITY_THROW);
    }

    private static void addBook(RegistryObject<Enchantment> ro) {
        ruby.bamboo.core.init.BambooItems.addCreativeStack(
                () -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ro.get(), ro.get().getMaxLevel())));
    }
}
