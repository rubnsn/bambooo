package ruby.bamboo.item;

/**
 * 腕輪専用カテゴリ (HiganEnchantType.BRACELET 相当)。
 * <p>
 * 1.21 で {@code EnchantmentCategory} は削除された。対象アイテムの指定は datapack JSON
 * ({@code data/bamboomod/enchantment/*.json} の {@code supported_items: "bamboomod:ninja_bracelet"})
 * へ移行したため、本クラスにランタイムの役割は無い。参照用に残すのみとし、他から参照しないこと。
 */
public final class BraceletEnchantmentCategory {
    /** 対象アイテムID。JSON 側の {@code supported_items} と一致させること。 */
    public static final String BRACELET_ITEM_ID = "bamboomod:ninja_bracelet";

    private BraceletEnchantmentCategory() {}
}
