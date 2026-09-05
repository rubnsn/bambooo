package ruby.bamboo.core.wish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import ruby.bamboo.BambooMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * datapack JSON loader for bamboo_wish.
 * port-spec-wish §6 準拠。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class WishJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final WishJsonLoader INSTANCE = new WishJsonLoader();

    private WishJsonLoader() {
        super(GSON, "bamboo_wish");
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        List<WishEntry> newEntries = new ArrayList<>();
        List<String> newPrefixes = new ArrayList<>(List.of("アイテム", "あいてむ", "item"));

        for (Map.Entry<ResourceLocation, JsonElement> e : map.entrySet()) {
            ResourceLocation rl = e.getKey();
            JsonElement elem = e.getValue();
            if (!elem.isJsonObject()) {
                continue;
            }
            JsonObject obj = elem.getAsJsonObject();
            // _meta.json
            if (rl.getPath().endsWith("_meta")) {
                if (obj.has("itemPrefixAliases") && obj.get("itemPrefixAliases").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("itemPrefixAliases");
                    newPrefixes.clear();
                    for (JsonElement a : arr) {
                        try {
                            newPrefixes.add(a.getAsString());
                        } catch (Exception ex) {
                            LOGGER.warn("Invalid prefix alias {}", a);
                        }
                    }
                    LOGGER.info("Wish prefixes loaded: {}", newPrefixes);
                }
                continue;
            }

            try {
                String id = obj.has("id") ? obj.get("id").getAsString() : rl.toString();
                String pattern = obj.has("pattern") ? obj.get("pattern").getAsString() : "";
                if (pattern.isEmpty()) {
                    LOGGER.warn("Wish entry {} missing pattern, skipping", id);
                    continue;
                }
                String match = obj.has("match") ? obj.get("match").getAsString() : "contains";
                boolean priority = obj.has("priority") && obj.get("priority").getAsBoolean();
                int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
                String message = obj.has("message") ? obj.get("message").getAsString() : null;
                List<WishEffect> effects = new ArrayList<>();

                if (obj.has("effect") && obj.get("effect").isJsonObject()) {
                    JsonObject eobj = obj.getAsJsonObject("effect");
                    String type = eobj.has("type") ? eobj.get("type").getAsString() : "";
                    if (!type.isEmpty()) {
                        JsonObject args = eobj.deepCopy();
                        args.remove("type");
                        effects.add(new WishEffect(type, args));
                    }
                } else if (obj.has("effects") && obj.get("effects").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("effects");
                    for (JsonElement ee : arr) {
                        if (!ee.isJsonObject()) continue;
                        JsonObject eobj = ee.getAsJsonObject();
                        String type = eobj.has("type") ? eobj.get("type").getAsString() : "";
                        if (type.isEmpty()) continue;
                        JsonObject args = eobj.deepCopy();
                        args.remove("type");
                        effects.add(new WishEffect(type, args));
                    }
                } else {
                    LOGGER.warn("Wish entry {} has no effect/effects", id);
                    continue;
                }

                if (effects.isEmpty()) {
                    LOGGER.warn("Wish entry {} has empty effects", id);
                    continue;
                }

                WishEntry we = new WishEntry(id, pattern, match, priority, weight, message, effects);
                newEntries.add(we);
            } catch (Exception ex) {
                LOGGER.error("Failed to parse wish json {}", rl, ex);
            }
        }

        WishManager.setEntries(newEntries, newPrefixes);
        LOGGER.info("Loaded {} wish entries", newEntries.size());
    }
}
