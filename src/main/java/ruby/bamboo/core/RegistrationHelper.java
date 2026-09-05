package ruby.bamboo.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import ruby.bamboo.BambooMod;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 汎用ブロック/アイテム登録ヘルパー。
 * <p>
 * 旧版(1.10.2)の ClassFinder + アノテーション走査 + GameRegistry.register の
 * 暗黙的システムを廃止し、1.20.1標準の DeferredRegister ベースの明示的登録に置換した。
 * 1.21.1 (NeoForge) では RegistryObject が DeferredBlock/DeferredItem/Supplier に置換された。
 * <p>
 * 使い方:
 * <pre>
 * // 1. ブロック登録 (BlockItemも自動生成される)
 * public static final DeferredBlock&lt;Block&gt; STRAW_BLOCK =
 *         BambooBlocks.registerBlock("straw_block", () -&gt; new Block(BlockBehaviour.Properties.of()));
 *
 * // 2. BlockItemを持たないブロック(内部用など)
 * public static final DeferredBlock&lt;Block&gt; INTERNAL =
 *         BambooBlocks.registerBlockNoItem("internal_block", () -&gt; new Block(...));
 *
 * // 3. 通常アイテム
 * public static final DeferredItem&lt;Item&gt; STRAW = BambooItems.register("straw", () -&gt; new Item(new Item.Properties()));
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
     * @return ブロックの DeferredBlock
     */
    public static <B extends Block> DeferredBlock<B> registerWithItem(String name,
            Supplier<? extends B> factory,
            Function<Supplier<B>, Item> itemFactory) {
        DeferredBlock<B> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> itemFactory.apply(block));
        return block;
    }

    /**
     * 標準的な BlockItem 付きでブロックを登録する。
     */
    public static DeferredBlock<Block> registerWithDefaultItem(String name, Supplier<? extends Block> factory,
            Item.Properties props) {
        DeferredBlock<Block> block = BambooMod.BLOCKS.register(name, factory);
        BambooMod.ITEMS.register(name, () -> new net.minecraft.world.item.BlockItem(block.get(), props));
        return block;
    }

    /**
     * BlockItem を生成せずブロックのみ登録する(流体・内部ブロック等)。
     */
    public static <B extends Block> DeferredBlock<B> registerNoItem(String name, Supplier<? extends B> factory) {
        return BambooMod.BLOCKS.register(name, factory);
    }

    /**
     * 通常アイテムを登録する。
     */
    public static <I extends Item> DeferredItem<I> registerItem(String name, Supplier<? extends I> factory) {
        return BambooMod.ITEMS.register(name, factory);
    }

    /**
     * 登録系 DeferredRegister を mod バスに接続する。メインクラスから一度だけ呼ぶ。
     */
    public static void attach(IEventBus modEventBus) {
        BambooMod.BLOCKS.register(modEventBus);
        BambooMod.ITEMS.register(modEventBus);
    }
}
