package ruby.bamboo.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * バイオーム名の動的検索。日本語別名（ひらがな正規化）＋英語でマッチ。
 * WishManager のバイオーム願い用。
 */
public final class WishBiomeSearch {

    private static final Map<ResourceLocation, List<String>> BIOME_ALIASES = new HashMap<>();

    static {
        // Overworld
        add("minecraft:plains", "平原", "へいげん", "plains");
        add("minecraft:sunflower_plains", "ひまわり平原", "sunflower");
        add("minecraft:snowy_plains", "雪原", "ゆきげん", "雪の平原", "snowy plains", "snowy");
        add("minecraft:ice_spikes", "氷のトゲ", "ice spikes");
        add("minecraft:desert", "砂漠", "さばく", "desert");
        add("minecraft:swamp", "湿地", "しつち", "沼", "swamp");
        add("minecraft:mangrove_swamp", "マングローブの沼地", "mangrove");
        add("minecraft:forest", "森", "もり", "forest");
        add("minecraft:flower_forest", "花の森", "flower forest");
        add("minecraft:birch_forest", "樺の森", "かばのもり", "birch");
        add("minecraft:dark_forest", "暗い森", "くらいもり", "dark forest");
        add("minecraft:old_growth_birch_forest", "樺の原生林", "old birch");
        add("minecraft:old_growth_pine_taiga", "マツの原生林", "pine taiga");
        add("minecraft:old_growth_spruce_taiga", "トウヒの原生林", "spruce taiga");
        add("minecraft:taiga", "タイガ", "taiga");
        add("minecraft:snowy_taiga", "雪のタイガ", "snowy taiga");
        add("minecraft:savanna", "サバンナ", "savanna");
        add("minecraft:savanna_plateau", "サバンナの高原", "savanna plateau");
        add("minecraft:windswept_hills", "山岳", "さんがく", "山", "やま", "hills", "windswept hills", "mountain");
        add("minecraft:windswept_gravelly_hills", "風化した山岳", "gravelly hills");
        add("minecraft:windswept_forest", "風化した森", "windswept forest");
        add("minecraft:windswept_savanna", "風化したサバンナ");
        add("minecraft:jungle", "ジャングル", "jungle");
        add("minecraft:sparse_jungle", "疎なジャングル", "sparse jungle");
        add("minecraft:bamboo_jungle", "竹林", "ちくりん", "竹ジャングル", "bamboo jungle");
        add("minecraft:river", "川", "かわ", "river");
        add("minecraft:frozen_river", "凍った川", "frozen river");
        add("minecraft:beach", "浜辺", "はまべ", "砂浜", "beach");
        add("minecraft:snowy_beach", "雪の浜辺", "snowy beach");
        add("minecraft:stony_shore", "石の海岸", "stony shore");
        add("minecraft:ocean", "海洋", "かいよう", "海", "うみ", "ocean");
        add("minecraft:cold_ocean", "冷たい海洋", "cold ocean");
        add("minecraft:deep_ocean", "深海", "deep ocean");
        add("minecraft:lukewarm_ocean", "ぬるい海洋", "lukewarm");
        add("minecraft:warm_ocean", "暖かい海洋", "warm ocean");
        add("minecraft:deep_cold_ocean", "深い冷たい海洋");
        add("minecraft:deep_lukewarm_ocean", "深いぬるい海洋");
        add("minecraft:deep_frozen_ocean", "深い凍った海洋");
        add("minecraft:frozen_ocean", "凍った海洋", "frozen ocean");
        add("minecraft:mushroom_fields", "きのこ島", "mushroom", "きのこげんや");
        add("minecraft:badlands", "荒地", "あれち", "メサ", "badlands", "mesa");
        add("minecraft:eroded_badlands", "浸食された荒地", "eroded badlands");
        add("minecraft:wooded_badlands", "森のある荒地", "wooded badlands");
        add("minecraft:dripstone_caves", "鍾乳洞", "しょうにゅうどう", "dripstone");
        add("minecraft:lush_caves", "繁茂した洞窟", "lush caves");
        add("minecraft:deep_dark", "深層暗黒", "deep dark");
        add("minecraft:meadow", "草地", "くさち", "meadow");
        add("minecraft:grove", "林", "はやし", "grove");
        add("minecraft:snowy_slopes", "雪の斜面", "snowy slopes");
        add("minecraft:frozen_peaks", "凍った山頂", "frozen peaks");
        add("minecraft:jagged_peaks", "尖った山頂", "jagged peaks");
        add("minecraft:stony_peaks", "石の山頂", "stony peaks");
        add("minecraft:cherry_grove", "桜の林", "さくらのもり", "cherry grove", "桜");
        // Nether
        add("minecraft:nether_wastes", "ネザーの荒地", "ネザー荒地", "nether wastes", "ネザー");
        add("minecraft:soul_sand_valley", "ソウルサンドの谷", "soul sand valley", "ソウルサンドバレー");
        add("minecraft:crimson_forest", "真紅の森", "しんくのもり", "crimson forest", "真紅");
        add("minecraft:warped_forest", "歪んだ森", "ゆがんだもり", "warped forest", "歪み");
        add("minecraft:basalt_deltas", "玄武岩の三角州", "げんぶがん", "玄武岩", "basalt deltas");
        // End
        add("minecraft:the_end", "エンド", "the end", "end");
        add("minecraft:end_highlands", "エンドの高地", "end highlands");
        add("minecraft:end_midlands", "エンドの中地", "end midlands");
        add("minecraft:small_end_islands", "小さなエンド島", "small end islands");
        add("minecraft:end_barrens", "エンドの荒地", "end barrens");
    }

    private static void add(String rl, String... aliases) {
        ResourceLocation id = ResourceLocation.parse(rl);
        List<String> list = BIOME_ALIASES.computeIfAbsent(id, k -> new ArrayList<>());
        for (String a : aliases) list.add(a);
        // also add rl path itself as alias
        list.add(id.getPath());
        list.add(id.toString());
    }

    private WishBiomeSearch() {}

    /**
     * normalizedInput (WishNormalizer済み) から最も適したバイオームを返す。見つからなければ null。
     * 長い別名を優先（「暗い森」 > 「森」）。
     */
    public static ResourceLocation findBest(String normalizedInput, ServerLevel level) {
        if (normalizedInput == null || normalizedInput.isEmpty()) return null;
        String normInput = normalizedInput;
        ResourceLocation best = null;
        int bestLen = -1;
        for (Map.Entry<ResourceLocation, List<String>> e : BIOME_ALIASES.entrySet()) {
            ResourceLocation rl = e.getKey();
            for (String alias : e.getValue()) {
                String normAlias = WishNormalizer.normalize(alias);
                if (normAlias.isEmpty()) continue;
                if (normInput.contains(normAlias)) {
                    int len = normAlias.length();
                    // prefer longer alias (more specific)
                    if (len > bestLen) {
                        // also ensure biome exists in registry (avoid stale)
                        var key = net.minecraft.resources.ResourceKey.create(Registries.BIOME, rl);
                        var reg = level.registryAccess().registryOrThrow(Registries.BIOME);
                        if (reg.containsKey(key) || rl.getNamespace().equals("minecraft")) {
                            best = rl;
                            bestLen = len;
                        }
                    }
                }
            }
        }
        return best;
    }
}
