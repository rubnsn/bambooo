package ruby.bamboo.core.init;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import ruby.bamboo.BambooMod;

/**
 * 腕輪エンチャント12種 (HiganEnchant 13種の移植。flash_jump は腕輪所持能力へ移行し対象外)。
 * <p>
 * 1.21 では Enchantment は datapack レジストリ化したため、コード登録
 * ({@code DeferredRegister<Enchantment>} / {@code Enchantment(Rarity, EnchantmentCategory, EquipmentSlot...)}
 * / {@code EnchantmentCategory.create} / 旧形 {@code EnchantmentInstance(Enchantment, int)}) は使えない。
 * 実体は {@code data/bamboomod/enchantment/*.json} 12種が SSOT であり、本クラスは
 * {@link ResourceKey} 定数 + クリエタブ用エンチャ本追加 + 呼び出し側共通ヘルパーのみを持つ。
 * <p>
 * 旧 {@code enchant/} 配下のサブクラス (EnchantmentBase + 12種) は他ファイルから参照が無かったため削除済み。
 * 旧 {@code getFullname} の {@code "_ex"} 表示カスタムは 1.21 では再現不可のため廃止
 * (lang の {@code _ex} エントリは harmless のため残置)。
 */
public final class BambooEnchantments {

    public static final ResourceKey<Enchantment> QUICK_THROW = key("quick_throw");
    public static final ResourceKey<Enchantment> POWER_THROW = key("power_throw");
    public static final ResourceKey<Enchantment> CRITICAL_THROW = key("critical_throw");
    public static final ResourceKey<Enchantment> POISON_THROW = key("poison_throw");
    public static final ResourceKey<Enchantment> SNIPE_THROW = key("snipe_throw");
    public static final ResourceKey<Enchantment> ECONOMY_BRACELET = key("economy_bracelet");
    public static final ResourceKey<Enchantment> UNBREAKING_BRACELET = key("unbreaking_bracelet");
    public static final ResourceKey<Enchantment> PICKPOCKET = key("pickpocket");
    public static final ResourceKey<Enchantment> DOUBLE_THROW = key("double_throw");
    public static final ResourceKey<Enchantment> TRIPLE_THROW = key("triple_throw");
    public static final ResourceKey<Enchantment> FLAME_THROW = key("flame_throw");
    public static final ResourceKey<Enchantment> INFINITY_THROW = key("infinity_throw");

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
    }

    private BambooEnchantments() {
    }

    public static void init() {
        // クリエタブへエンチャ本を追加（最大レベルのみ、調整用）。
        // Holder 解決には RegistryAccess が要るため、タブ描画時まで遅延させる Supplier で登録する。
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

    private static void addBook(ResourceKey<Enchantment> key) {
        BambooItems.addCreativeStack(() -> createBook(key));
    }

    /**
     * タブ描画時に実行される遅延解決。サーバ不在時 (マルチプレイのクライアント等) は
     * Holder が取れないため {@link ItemStack#EMPTY} を返し、BambooMod 側で skip させる。
     */
    private static ItemStack createBook(ResourceKey<Enchantment> key) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ItemStack.EMPTY;
        }
        Holder<Enchantment> holder = server.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, holder.value().getMaxLevel()));
    }

    /**
     * 共通ヘルパー: 指定エンチャントのレベルを ItemStack から取得する。
     * <p>
     * 呼び出し例 (NinjaBraceletItem 等):
     * <pre>{@code
     * int lv = BambooEnchantments.getLevel(level.registryAccess(), BambooEnchantments.POWER_THROW, stack);
     * }</pre>
     * 呼び出し例 (ServerLevel を持つイベントハンドラ):
     * <pre>{@code
     * int lv = BambooEnchantments.getLevel(serverLevel.registryAccess(), BambooEnchantments.PICKPOCKET, stack);
     * }</pre>
     *
     * @param provider {@code level.registryAccess()} 等 (HolderLookup.Provider)
     * @param key      BambooEnchantments の ResourceKey 定数12種のいずれか
     * @param stack    調べる ItemStack
     * @return エンチャントレベル (無ければ 0)。key 未登録時は例外 (mod 内包 datapack 由来のため通常起きない)
     */
    public static int getLevel(HolderLookup.Provider provider, ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> holder = provider.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }
}
