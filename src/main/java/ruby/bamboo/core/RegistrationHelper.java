package ruby.bamboo.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 汎用ブロック/アイテム登録ヘルパー。
 * <p>
 * 旧版(1.10.2)の ClassFinder + アノテーション走査 + GameRegistry.register の
 * 暗黙的システムを廃止し、1.20.1標準の DeferredRegister ベースの明示的登録に置換した。
 * <p>
 * 使い方:
 * <pre>
 * // 1. ブロック登録 (BlockItemも自動生成される)
 * public static final RegistryObject&lt;Block&gt; STRAW_BLOCK =
 *         BambooBlocks.registerBlock("straw_block", () -&gt; new Block(BlockBehaviour.Properties.of()));
 *
 * // 2. BlockItemを持たないブロック(内部用など)
 * public static final RegistryObject&lt;Block&gt; INTERNAL =
 *         BambooBlocks.registerBlockNoItem("internal_block", () -&gt; new Block(...));
 *
 * // 3. 通常アイテム
 * public static final RegistryObject&lt;Item&gt; STRAW = BambooItems.register("straw", () -&gt; new Item(new Item.Properties()));
 * </pre>
 */
public final class RegistrationHelper {

    private RegistrationHelper() {
    }

    /**
     * ブロックを登録し、同名の {@link net.minecraft.world.item.BlockItem} も自動登録する。
     *
     * @param name        登録名 (小文字スネークケース)
     * @param factory     ブロックのファクトリ
     * @param itemFactory BlockItem を生成する関数 (第1引数にブロックを受け取る)
     * @param <B>         ブロック型
     * @return ブロックの RegistryObject
     */
    public static <B extends Block> RegistryObject<B> registerWithItem(String name,
            Supplier<? extends B> factory,
            Function<Supplier<B>, Item> itemFactory) {
        RegistryObject<B> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> itemFactory.apply(() -> block.get()));
        return block;
    }

    /**
     * 標準的な BlockItem 付きでブロックを登録する。
     */
    public static RegistryObject<Block> registerWithDefaultItem(String name, Supplier<? extends Block> factory,
            Item.Properties props) {
        RegistryObject<Block> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> new net.minecraft.world.item.BlockItem(block.get(), props));
        return block;
    }

    /**
     * BlockItem を生成せずブロックのみ登録する(流体・内部ブロック等)。
     */
    public static <B extends Block> RegistryObject<B> registerNoItem(String name, Supplier<? extends B> factory) {
        return BambooMod.BLOCKS.register(name, factory);
    }

    /**
     * 通常アイテムを登録する。
     */
    public static <I extends Item> RegistryObject<I> registerItem(String name, Supplier<? extends I> factory) {
        return BambooMod.ITEMS.register(name, factory);
    }

    /**
     * 登録系 DeferredRegister を mod バスに接続する。メインクラスから一度だけ呼ぶ。
     */
    public static void attach(IEventBus modEventBus) {
        BambooMod.BLOCKS.register(modEventBus);
        BambooMod.ITEMS.register(modEventBus);
        ForgeRegistries.BLOCKS.getEntries(); // noop: クラスロード保証
    }
}
