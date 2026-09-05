package ruby.bamboo.core.init;

import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import ruby.bamboo.BambooMod;
import ruby.bamboo.gui.CampfireMenu;
import ruby.bamboo.gui.MillStoneMenu;

/**
 * MenuType 登録 (石臼GUI用に新規追加)。
 * <p>
 * JPChest はバニラ ChestMenu を流用するため MenuType 不要だったが、
 * 石臼は出力専用スロット・containerData を持つ独自 GUI のため専用 MenuType が必要。
 * <p>
 * DeferredRegister 本体は {@link ruby.bamboo.BambooMod#MENUS} を使用する。
 */
public final class BambooMenus {

    /** 石臼のメニュー。クライアント側ファクトリは (id, inv) 2引数版 */
    public static final Supplier<MenuType<MillStoneMenu>> MILL_STONE = BambooMod.MENUS.register("mill_stone",
            () -> new MenuType<>(MillStoneMenu::new, FeatureFlags.VANILLA_SET));

    /** 囲炉裏のメニュー。クライアント側ファクトリは (id, inv) 2引数版 */
    public static final Supplier<MenuType<CampfireMenu>> CAMPFIRE = BambooMod.MENUS.register("campfire",
            () -> new MenuType<>(CampfireMenu::new, FeatureFlags.VANILLA_SET));

    /** 袋のメニュー (1スロット)。クライアント側ファクトリは (id, inv) 2引数版 */
    public static final Supplier<MenuType<ruby.bamboo.gui.SackMenu>> SACK = BambooMod.MENUS.register("sack",
            () -> new MenuType<>(ruby.bamboo.gui.SackMenu::new, FeatureFlags.VANILLA_SET));

    /**
     * 静的初期化順序の保証用ダミー。BambooMod コンストラクタから呼ばれる。
     */
    public static void init() {
        // static フィールド初期化は本クラスがロードされた時点で完了している
    }
}