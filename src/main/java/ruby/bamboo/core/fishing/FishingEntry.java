package ruby.bamboo.core.fishing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 釣れデータ 1 件。バイオーム別テーブル 1 行相当。
 * <p>
 * 仕様書 §3, §4。datapack JSON でもこの型にデシリアライズされるが、
 * MVP は FishingManager 内でハードコードしたデフォルトテーブルを使用する。
 */
public class FishingEntry {

    public enum Category {
        FISH, JUNK, TREASURE
    }

    public enum MovePattern {
        SMOOTH, DART, SINKER, MIXED
    }

    public final ResourceLocation id;
    public final ResourceLocation itemId;
    public final Category category;
    public final int weight;
    public final int reqBitePower;
    public final boolean nightOnly;
    public final int stamina;
    public final int power;
    public final MovePattern move;

    /**
     * バイオーム好みタグ (例: "is_ocean", "is_river", "is_warm_ocean" 等)。
     * 空ならバイオーム補正なし。
     */
    public final String preferredBiomeTag;
    /** 好みバイオームでの重量倍率 */
    public final float preferredMultiplier;

    public FishingEntry(ResourceLocation id, ResourceLocation itemId, Category category,
                        int weight, int reqBitePower,
                        boolean nightOnly,
                        int stamina, int power, MovePattern move,
                        String preferredBiomeTag, float preferredMultiplier) {
        this.id = id;
        this.itemId = itemId;
        this.category = category;
        this.weight = weight;
        this.reqBitePower = reqBitePower;
        this.nightOnly = nightOnly;
        this.stamina = stamina;
        this.power = power;
        this.move = move;
        this.preferredBiomeTag = preferredBiomeTag;
        this.preferredMultiplier = preferredMultiplier;
    }

    public boolean isFish() {
        return category == Category.FISH;
    }

    public static FishingEntry fish(ResourceLocation id, ResourceLocation itemId,
                                    int weight, int req,
                                    boolean nightOnly,
                                    int stamina, int power, MovePattern move,
                                    String preferredTag, float mult) {
        return new FishingEntry(id, itemId, Category.FISH, weight, req,
                nightOnly, stamina, power, move, preferredTag, mult);
    }

    public static FishingEntry junk(ResourceLocation id, ResourceLocation itemId, int weight, int req) {
        return new FishingEntry(id, itemId, Category.JUNK, weight, req,
                false, 8, 0, MovePattern.SMOOTH, null, 1f);
    }

    public static FishingEntry treasure(ResourceLocation id, ResourceLocation itemId, int weight, int req) {
        return new FishingEntry(id, itemId, Category.TREASURE, weight, req,
                false, 10, 1, MovePattern.SMOOTH, null, 1f);
    }
}
