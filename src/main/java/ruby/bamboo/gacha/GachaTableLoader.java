package ruby.bamboo.gacha;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * datapack式ガチャテーブル ({@code data/bamboomod/bamboo_gacha/{common,rare,super_rare}.json})。
 * <p>
 * 形式: {@code { "entries": [ {"id":"minecraft:stone","count":[8,16],"weight":10}, ... ] }}。
 * count は [min,max] または単数可。reload対応 (/reload で反映)。
 * JSON不正・空の枠は内蔵デフォルトで補完する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GachaTableLoader extends SimpleJsonResourceReloadListener {
    public static final String DIR = "bamboo_gacha";
    public static final GachaTableLoader INSTANCE = new GachaTableLoader();

    private final Map<GachaRarity, List<GachaEntry>> tables = new EnumMap<>(GachaRarity.class);

    private GachaTableLoader() {
        super(new com.google.gson.Gson(), DIR);
        resetToDefaults();
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public List<GachaEntry> table(GachaRarity rarity) {
        List<GachaEntry> t = tables.get(rarity);
        return t == null || t.isEmpty() ? defaultsFor(rarity) : t;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager,
            ProfilerFiller profiler) {
        Map<GachaRarity, List<GachaEntry>> next = new EnumMap<>(GachaRarity.class);
        for (GachaRarity r : GachaRarity.values()) {
            next.put(r, new ArrayList<>());
        }
        for (var e : map.entrySet()) {
            // ファイル名 common.json / rare.json / super_rare.json → 枠判定
            GachaRarity r = rarityOf(e.getKey().getPath());
            if (r == null) {
                continue;
            }
            try {
                JsonObject root = e.getValue().getAsJsonObject();
                JsonArray arr = root.has("entries") ? root.getAsJsonArray("entries")
                        : new JsonArray();
                for (JsonElement el : arr) {
                    GachaEntry en = parseEntry(el.getAsJsonObject());
                    if (en != null) {
                        next.get(r).add(en);
                    }
                }
            } catch (Exception ex) {
                BambooMod.LOGGER.warn("Skip invalid gacha table {}: {}", e.getKey(), ex.toString());
            }
        }
        for (GachaRarity r : GachaRarity.values()) {
            List<GachaEntry> l = next.get(r);
            tables.put(r, (l == null || l.isEmpty()) ? defaultsFor(r) : l);
        }
        BambooMod.LOGGER.info("Gacha tables reloaded: C={} R={} SR={}",
                tables.get(GachaRarity.COMMON).size(),
                tables.get(GachaRarity.RARE).size(),
                tables.get(GachaRarity.SUPER_RARE).size());
    }

    private static GachaRarity rarityOf(String path) {
        // path は "bamboo_gacha/common" 形式 (拡張子なし)
        String name = path.substring(path.lastIndexOf('/') + 1);
        return switch (name) {
            case "common" -> GachaRarity.COMMON;
            case "rare" -> GachaRarity.RARE;
            case "super_rare" -> GachaRarity.SUPER_RARE;
            default -> null;
        };
    }

    private static GachaEntry parseEntry(JsonObject o) {
        try {
            ResourceLocation id = ResourceLocation.parse(o.get("id").getAsString());
            int min = 1, max = 1;
            JsonElement c = o.get("count");
            if (c != null) {
                if (c.isJsonArray()) {
                    JsonArray a = c.getAsJsonArray();
                    min = a.get(0).getAsInt();
                    max = a.size() > 1 ? a.get(1).getAsInt() : min;
                } else {
                    min = max = c.getAsInt();
                }
            }
            int weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
            return new GachaEntry(id, min, max, weight);
        } catch (Exception ex) {
            BambooMod.LOGGER.warn("Skip invalid gacha entry: {}", ex.toString());
            return null;
        }
    }

    private void resetToDefaults() {
        for (GachaRarity r : GachaRarity.values()) {
            tables.put(r, defaultsFor(r));
        }
    }

    /** 内蔵デフォルト (datapack無し/reload前のフォールバック)。 */
    public static List<GachaEntry> defaultsFor(GachaRarity r) {
        List<GachaEntry> l = new ArrayList<>();
        switch (r) {
            case COMMON -> {
                // ノーマル: オーバーワールドを構成するブロック
                add(l, "minecraft:stone", 8, 16, 10);
                add(l, "minecraft:cobblestone", 8, 16, 10);
                add(l, "minecraft:dirt", 8, 16, 10);
                add(l, "minecraft:oak_log", 4, 8, 8);
                add(l, "minecraft:oak_planks", 8, 16, 8);
                add(l, "minecraft:sand", 8, 16, 6);
                add(l, "minecraft:gravel", 8, 16, 6);
                add(l, "minecraft:coal_ore", 2, 4, 4);
                add(l, "minecraft:iron_ore", 2, 4, 3);
                add(l, "minecraft:glass", 4, 8, 4);
                add(l, "minecraft:potato", 4, 8, 6);
                add(l, "minecraft:beetroot", 4, 8, 6);
                add(l, "minecraft:carrot", 4, 8, 6);
            }
            case RARE -> {
                // レア: モンスター素材
                add(l, "minecraft:rotten_flesh", 4, 8, 10);
                add(l, "minecraft:bone", 4, 8, 10);
                add(l, "minecraft:string", 4, 8, 8);
                add(l, "minecraft:gunpowder", 2, 6, 8);
                add(l, "minecraft:spider_eye", 1, 3, 6);
                add(l, "minecraft:ender_pearl", 1, 2, 4);
                add(l, "minecraft:blaze_rod", 1, 2, 3);
                add(l, "minecraft:slime_ball", 2, 4, 6);
                add(l, "minecraft:gold_ingot", 2, 4, 6);
                add(l, "minecraft:lapis_lazuli", 4, 8, 5);
                add(l, "minecraft:golden_apple", 1, 1, 2);
                add(l, "minecraft:golden_carrot", 2, 4, 5);
            }
            case SUPER_RARE -> {
                // SR: 希少寄り
                add(l, "minecraft:diamond", 1, 3, 8);
                add(l, "minecraft:emerald", 1, 3, 6);
                add(l, "minecraft:netherite_scrap", 1, 1, 2);
                add(l, "minecraft:ancient_debris", 1, 1, 1);
                add(l, "minecraft:totem_of_undying", 1, 1, 1);
                add(l, "bamboomod:maple_gem", 1, 1, 3);
                add(l, "bamboomod:ginkgo_gem", 1, 1, 3);
                add(l, "bamboomod:sakura_gem", 1, 1, 3);
                add(l, "minecraft:shulker_shell", 1, 2, 2);
                add(l, "bamboomod:wish_wand", 1, 1, 1);
            }
        }
        return l;
    }

    private static void add(List<GachaEntry> l, String id, int min, int max, int weight) {
        l.add(new GachaEntry(ResourceLocation.parse(id), min, max, weight));
    }
}
