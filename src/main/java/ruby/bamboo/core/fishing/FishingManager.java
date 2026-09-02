package ruby.bamboo.core.fishing;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 釣果抽選マネージャ。サーバー側でのみ使用。
 * <p>
 * 仕様書 §5-§6 準拠。キャスト時に呼び出され、釣果 1 件とサイズクラスをロールする。
 * データはハードコードのデフォルトテーブルを使用し、将来的に JSON リロードで差し替え可能。
 */
public final class FishingManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static List<FishingEntry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES = new ArrayList<>(buildDefaultEntries());
    }

    private FishingManager() {}

    public static List<FishingEntry> getEntries() {
        return new ArrayList<>(ENTRIES);
    }

    public static void setEntries(List<FishingEntry> entries) {
        ENTRIES = new ArrayList<>(entries);
        LOGGER.info("FishingManager loaded {} entries", ENTRIES.size());
    }

    private static List<FishingEntry> buildDefaultEntries() {
        List<FishingEntry> list = new ArrayList<>();
        // バニラ魚 4 種。ランクは bronze/silver/gold で抽選 (cm廃止)。
        // stamina / power は竹竿 25/s に対してバランス調整 (stamina 8-14 が適正)
        list.add(FishingEntry.fish(
                new ResourceLocation("bamboomod:cod"),
                new ResourceLocation("minecraft:cod"),
                10, 0,
                false,
                12, 1, FishingEntry.MovePattern.SMOOTH,
                "is_ocean", 1.8f));
        list.add(FishingEntry.fish(
                new ResourceLocation("bamboomod:salmon"),
                new ResourceLocation("minecraft:salmon"),
                10, 0,
                false,
                14, 1, FishingEntry.MovePattern.SMOOTH,
                "is_river", 1.8f));
        // tropical は暖かい海で重み増
        list.add(FishingEntry.fish(
                new ResourceLocation("bamboomod:tropical_fish"),
                new ResourceLocation("minecraft:tropical_fish"),
                6, 2,
                false,
                10, 1, FishingEntry.MovePattern.DART,
                null, 1f));
        list.add(FishingEntry.fish(
                new ResourceLocation("bamboomod:pufferfish"),
                new ResourceLocation("minecraft:pufferfish"),
                4, 1,
                false,
                10, 2, FishingEntry.MovePattern.SINKER,
                "is_ocean", 1.2f));
        // 夜限定: 例として pufferfish の夜亜種的扱いはせず、別枠でダミーを入れてもよいが MVP では無し
        // ゴミ (JUNK) - mcmod の junk 相当を簡略化
        list.add(FishingEntry.junk(
                new ResourceLocation("bamboomod:junk_stick"),
                new ResourceLocation("minecraft:stick"), 3, 0));
        list.add(FishingEntry.junk(
                new ResourceLocation("bamboomod:junk_string"),
                new ResourceLocation("minecraft:string"), 3, 0));
        list.add(FishingEntry.junk(
                new ResourceLocation("bamboomod:junk_bone"),
                new ResourceLocation("minecraft:bone"), 2, 0));
        // 宝箱 (TREASURE) - 木/鉄/金/ダイヤの金属価値で段階化。MVP では直接インゴット等を与える
        list.add(FishingEntry.treasure(
                new ResourceLocation("bamboomod:treasure_iron"),
                new ResourceLocation("minecraft:iron_ingot"), 2, 1));
        list.add(FishingEntry.treasure(
                new ResourceLocation("bamboomod:treasure_gold"),
                new ResourceLocation("minecraft:gold_ingot"), 1, 2));
        list.add(FishingEntry.treasure(
                new ResourceLocation("bamboomod:treasure_diamond"),
                new ResourceLocation("minecraft:diamond"), 1, 3));
        // warm 特化の魚がバイオームタグで強化されないものは、FishingManager.roll 内で追加補正を入れる
        return list;
    }

    public static class RollResult {
        public final FishingEntry entry;
        public final FishSize size;
        public final int startProgress;
        public final int fishStamina;
        public final int fishPower;
        public final FishingEntry.MovePattern movePattern;

        public RollResult(FishingEntry entry, FishSize size, int startProgress,
                          int fishStamina, int fishPower, FishingEntry.MovePattern movePattern) {
            this.entry = entry;
            this.size = size;
            this.startProgress = startProgress;
            this.fishStamina = fishStamina;
            this.fishPower = fishPower;
            this.movePattern = movePattern;
        }
    }

    /**
     * 釣果を 1 件ロールする。成功時は RollResult を返す。
     *
     * @param player      釣り人 (位置・バイオーム取得に使用)
     * @param level       サーバーレベル
     * @param pos         プレイヤー位置 (バイオーム判定)
     * @param bitePower   プレイヤー側バイトパワー (餌 + 月齢 + 雨 + 竿加算)
     * @param distance    飛距離ブロック数 (4-15)
     * @param random      乱数
     */
    public static RollResult roll(ServerPlayer player, ServerLevel level, BlockPos pos,
                                   int bitePower, int distance, RandomSource random) {
        if (ENTRIES.isEmpty()) {
            return null;
        }
        long dayTime = level.getDayTime() % 24000;
        boolean isNight = dayTime >= 13000 && dayTime <= 23000;

        // 1. 重み調整した候補リストを作成
        List<FishingEntry> candidates = new ArrayList<>();
        List<Integer> effectiveWeights = new ArrayList<>();
        int totalWeight = 0;
        for (FishingEntry e : ENTRIES) {
            if (e.nightOnly && !isNight) continue;
            int w = e.weight;
            // バイトパワー差による重み補正: req を大きく下回ると大幅減、上回ると微増なし (サイズで反映)
            if (bitePower < e.reqBitePower) {
                int deficit = e.reqBitePower - bitePower;
                // deficit 1 で 40%, 2 で 20%, 3 で 10% まで減少
                float factor = switch (deficit) {
                    case 1 -> 0.4f;
                    case 2 -> 0.2f;
                    default -> 0.1f;
                };
                w = Math.max(1, Math.round(w * factor));
            }
            // バイオーム好みタグの補正
            float biomeFactor = getBiomeFactor(level, pos, e);
            w = Math.max(1, Math.round(w * biomeFactor));
            // 飛距離ボーナス: 遠投ほど良い魚 (req 高い) が出やすい。req > 0 のエントリにのみ距離補正を掛ける
            if (distance >= 10 && e.reqBitePower >= 2) {
                float distFactor = 1.0f + (distance - 10) * 0.08f; // 15で +0.4
                w = Math.round(w * distFactor);
            } else if (distance <= 6 && e.category == FishingEntry.Category.TREASURE) {
                // 近投だと宝は出にくい
                w = Math.round(w * 0.7f);
                w = Math.max(1, w);
            }
            candidates.add(e);
            effectiveWeights.add(w);
            totalWeight += w;
        }
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        // 2. 重み付き抽選
        int pick = random.nextInt(totalWeight);
        FishingEntry chosen = null;
        int acc = 0;
        for (int i = 0; i < candidates.size(); i++) {
            acc += effectiveWeights.get(i);
            if (pick < acc) {
                chosen = candidates.get(i);
                break;
            }
        }
        if (chosen == null) chosen = candidates.get(candidates.size() - 1);

        // 3. サイズクラスをロール
        FishSize size = rollSize(bitePower, chosen.reqBitePower, random);

        // 4. ミニゲーム難度パラメータを決定
        int startProgress = computeStartProgress(distance);
        float sizeMult = switch (size) {
            case BRONZE -> 0.85f;
            case SILVER -> 1.0f;
            case GOLD -> 1.5f;
        };
        int stamina = Math.round(chosen.stamina * sizeMult);
        // fish power: そのままカテゴリで移譲、ゴールドなら +1
        int power = chosen.power;
        if (size == FishSize.GOLD) power += 1;
        // tropical のように暖かい海補正: tropical_fish は暖かい海以外で weight 半減相当をここでは既に factor 済みだが、
        // それでも選ばれた場合はそのまま返す

        return new RollResult(chosen, size, startProgress, stamina, power, chosen.move);
    }

    private static float getBiomeFactor(Level level, BlockPos pos, FishingEntry entry) {
        if (entry.preferredBiomeTag == null || entry.preferredBiomeTag.isEmpty()) return 1f;
        try {
            var biomeHolder = level.getBiome(pos);
            var biome = biomeHolder.value();
            // biomeHolder.is(...) でタグ判定
            boolean matches = false;
            switch (entry.preferredBiomeTag) {
                case "is_ocean" -> matches = biomeHolder.is(BiomeTags.IS_OCEAN);
                case "is_river" -> matches = biomeHolder.is(BiomeTags.IS_RIVER);
                case "is_warm_ocean" -> {
                    // 1.20.1 does not have IS_WARM_OCEAN tag exactly; approximate via biome tags or check biome key
                    // check if biome is warm ocean via resource location
                    ResourceLocation biomeId = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(biome);
                    if (biomeId != null) {
                        String path = biomeId.getPath();
                        matches = path.contains("warm_ocean") || path.contains("lukewarm");
                    }
                }
                default -> matches = false;
            }
            return matches ? entry.preferredMultiplier : 0.6f; // 非好みでは 0.6 倍
        } catch (Exception ex) {
            return 1f;
        }
    }

    private static FishSize rollSize(int bitePower, int req, RandomSource random) {
        int excess = bitePower - req;
        float pBronze = 0.35f, pSilver = 0.50f, pGold = 0.15f;
        // excess 1 ごとに 10% を BRONZE -> GOLD へ移動
        if (excess > 0) {
            for (int i = 0; i < excess && i < 4; i++) {
                float shift = 0.10f;
                // BRONZE から取れなければ SILVER から取る
                if (pBronze >= shift) {
                    pBronze -= shift;
                    pGold += shift;
                } else if (pSilver >= shift) {
                    pSilver -= shift;
                    pGold += shift;
                }
            }
        } else if (excess < 0) {
            // 逆シフト: GOLD -> BRONZE
            for (int i = 0; i < -excess && i < 4; i++) {
                float shift = 0.08f;
                if (pGold >= shift) {
                    pGold -= shift;
                    pBronze += shift;
                } else if (pSilver >= shift) {
                    pSilver -= shift;
                    pBronze += shift;
                }
            }
        }
        // clamp
        pBronze = Math.max(0.05f, pBronze);
        pGold = Math.max(0.05f, pGold);
        // normalize
        float sum = pBronze + pSilver + pGold;
        pBronze /= sum; pSilver /= sum; pGold /= sum;
        float r = random.nextFloat();
        if (r < pBronze) return FishSize.BRONZE;
        if (r < pBronze + pSilver) return FishSize.SILVER;
        return FishSize.GOLD;
    }

    public static int computeStartProgress(int distance) {
        // 即逃げ防止で 20 から開始。距離は抽選のみに使用
        return 20;
    }

    /**
     * 釣果の ItemStack を生成する。NBT としてランクを付与する。
     */
    public static net.minecraft.world.item.ItemStack createCatchStack(RollResult result, RandomSource random) {
        ResourceLocation itemId = result.entry.itemId;
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            LOGGER.warn("Unknown catch item {}", itemId);
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        // ランク付与: FISH のみ
        if (result.entry.category == FishingEntry.Category.FISH) {
            stack.getOrCreateTag().putString(FishSize.TAG_KEY, result.size.tagValue);
        }
        return stack;
    }
}
