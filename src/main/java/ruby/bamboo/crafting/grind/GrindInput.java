package ruby.bamboo.crafting.grind;

import java.util.Optional;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 石臼レシピの入力条件 (旧 IGrindInputItem / GrindInputItem / GrindInputOreItem の置き換え)。
 * <p>
 * 旧版は「Item+damage」「鉱石辞書名」の2系統だったが、1.20.1 ではメタデータバリアントが
 * 廃止されたため「アイテム一致」「タグ一致」の2系統に整理した。
 * <p>
 * count は一回の粉砕に必要な個数 (旧 getInput().getStackSize())。
 * マップのキー同一性には count を含めない (旧実装と同じく、検索後に必要数判定を行う)。
 */
public final class GrindInput {

    /** タグ一致の場合は非null、アイテム一致の場合はnull */
    private final Optional<TagKey<Item>> tag;
    private final Item item;
    private final int count;

    private GrindInput(Optional<TagKey<Item>> tag, Item item, int count) {
        this.tag = tag;
        this.item = item;
        this.count = count;
    }

    /** アイテム一致の入力条件 (旧 GrindInputItem 相当) */
    public static GrindInput of(Item item, int count) {
        return new GrindInput(Optional.empty(), item, count);
    }

    /** タグ一致の入力条件 (旧 GrindInputOreItem 相当) */
    public static GrindInput ofTag(TagKey<Item> tag, int count) {
        return new GrindInput(Optional.of(tag), net.minecraft.world.item.Items.AIR, count);
    }

    public boolean isTag() {
        return tag.isPresent();
    }

    public Optional<TagKey<Item>> tag() {
        return tag;
    }

    public Item item() {
        return item;
    }

    public int count() {
        return count;
    }

    /**
     * パーティクル表示等に使う代表アイテム。
     * タグ一致の場合はタグ内の最初のアイテム (旧 OreDictionary.getOres(name).get(0) 相当)。
     */
    public Item exemplarItem() {
        if (tag.isPresent()) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getTag(tag.get())
                    .map(holders -> holders.iterator().next().value())
                    .orElse(item);
        }
        return item;
    }

    /** 入力スタックがこの条件に合致するか (個数は問わない) */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return tag.isPresent() ? stack.is(tag.get()) : stack.is(item);
    }
}