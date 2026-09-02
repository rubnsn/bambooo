package ruby.bamboo.core.fishing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import ruby.bamboo.BambooMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * datapack loader for fishing/fish. Each file = one FishingEntry.
 * path: data/<namespace>/fishing/fish/<id>.json
 * If no files found, FishingManager keeps buildDefaultEntries fallback.
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FishingFishJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final FishingFishJsonLoader INSTANCE = new FishingFishJsonLoader();

    private FishingFishJsonLoader() {
        super(GSON, "fishing/fish");
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        List<FishingEntry> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
            ResourceLocation rl = e.getKey();
            JsonElement elem = e.getValue();
            if (!elem.isJsonObject()) {
                LOGGER.warn("Fishing fish json {} not an object, skipping", rl);
                continue;
            }
            JsonObject obj = elem.getAsJsonObject();
            try {
                FishingEntry entry = parseEntry(rl, obj);
                if (entry != null) loaded.add(entry);
            } catch (Exception ex) {
                LOGGER.error("Failed to parse fishing fish json {}", rl, ex);
            }
        }
        if (loaded.isEmpty()) {
            LOGGER.warn("No fishing/fish json found, keeping default entries ({} entries)", FishingManager.getEntries().size());
            return;
        }
        FishingManager.setEntries(loaded);
        LOGGER.info("Loaded {} fishing fish entries", loaded.size());
    }

    private static FishingEntry parseEntry(ResourceLocation rl, JsonObject obj) {
        // id: defaults to rl
        ResourceLocation id = rl;
        if (obj.has("id")) {
            try {
                id = new ResourceLocation(obj.get("id").getAsString());
            } catch (Exception ex) {
                LOGGER.warn("Invalid id in {}: {}", rl, obj.get("id").getAsString());
                return null;
            }
        }
        if (!obj.has("item")) {
            LOGGER.warn("Fishing fish {} missing 'item', skipping", rl);
            return null;
        }
        ResourceLocation itemId;
        try {
            itemId = new ResourceLocation(obj.get("item").getAsString());
        } catch (Exception ex) {
            LOGGER.warn("Invalid item in {}: {}", rl, obj.get("item").getAsString());
            return null;
        }
        String catStr = obj.has("category") ? obj.get("category").getAsString().toUpperCase() : "FISH";
        FishingEntry.Category category;
        try {
            category = FishingEntry.Category.valueOf(catStr);
        } catch (Exception ex) {
            LOGGER.warn("Invalid category {} in {}, default FISH", catStr, rl);
            category = FishingEntry.Category.FISH;
        }
        int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
        if (weight <= 0) weight = 1;
        int reqBitePower = obj.has("reqBitePower") ? obj.get("reqBitePower").getAsInt() : 0;
        boolean nightOnly = obj.has("nightOnly") && obj.get("nightOnly").getAsBoolean();
        int stamina = obj.has("stamina") ? obj.get("stamina").getAsInt() : 10;
        int power = obj.has("power") ? obj.get("power").getAsInt() : 1;
        String moveStr = obj.has("move") ? obj.get("move").getAsString().toUpperCase() : "SMOOTH";
        FishingEntry.MovePattern move;
        try {
            move = FishingEntry.MovePattern.valueOf(moveStr);
        } catch (Exception ex) {
            LOGGER.warn("Invalid move {} in {}, default SMOOTH", moveStr, rl);
            move = FishingEntry.MovePattern.SMOOTH;
        }
        String preferredBiomeTag = null;
        if (obj.has("preferredBiomeTag") && !obj.get("preferredBiomeTag").isJsonNull()) {
            preferredBiomeTag = obj.get("preferredBiomeTag").getAsString();
            if (preferredBiomeTag.isEmpty()) preferredBiomeTag = null;
        }
        float preferredMultiplier = obj.has("preferredMultiplier") ? obj.get("preferredMultiplier").getAsFloat() : 1f;
        if (preferredMultiplier <= 0) preferredMultiplier = 1f;

        return new FishingEntry(id, itemId, category, weight, reqBitePower, nightOnly, stamina, power, move, preferredBiomeTag, preferredMultiplier);
    }
}
