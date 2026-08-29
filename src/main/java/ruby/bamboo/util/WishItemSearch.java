package ruby.bamboo.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 動的アイテム名検索 §4.5。
 */
public final class WishItemSearch {

    private WishItemSearch() {
    }

    public static Item findItem(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return null;
        }
        List<Item> exact = new ArrayList<>();
        List<ItemWithName> partials = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            String display = stack.getHoverName().getString();
            if (display == null || display.isEmpty()) {
                continue;
            }
            String norm = WishNormalizer.normalize(display);
            if (norm.isEmpty()) {
                continue;
            }
            if (norm.equals(normalizedQuery)) {
                exact.add(item);
            } else if (norm.contains(normalizedQuery)) {
                partials.add(new ItemWithName(item, norm));
            }
        }

        if (!exact.isEmpty()) {
            exact.sort(Comparator.comparingInt(WishItemSearch::namespacePriority)
                    .thenComparingInt(WishItemSearch::registryNameLength)
                    .thenComparing(WishItemSearch::registryName));
            return exact.get(0);
        }

        if (!partials.isEmpty()) {
            int bestDist = Integer.MAX_VALUE;
            List<Item> best = new ArrayList<>();
            for (ItemWithName p : partials) {
                int dist = levenshtein(normalizedQuery, p.normName);
                if (dist < bestDist) {
                    bestDist = dist;
                    best.clear();
                    best.add(p.item);
                } else if (dist == bestDist) {
                    best.add(p.item);
                }
            }
            best.sort(Comparator.comparingInt(WishItemSearch::namespacePriority)
                    .thenComparingInt(WishItemSearch::registryNameLength)
                    .thenComparing(WishItemSearch::registryName));
            return best.get(0);
        }
        return null;
    }

    private static int namespacePriority(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        if (rl == null) {
            return 2;
        }
        String ns = rl.getNamespace();
        if ("minecraft".equals(ns)) {
            return 0;
        }
        if ("bamboomod".equals(ns)) {
            return 1;
        }
        return 2;
    }

    private static int registryNameLength(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl == null ? Integer.MAX_VALUE : rl.getPath().length();
    }

    private static String registryName(Item item) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl == null ? "" : rl.toString();
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    private static class ItemWithName {
        final Item item;
        final String normName;
        ItemWithName(Item item, String normName) {
            this.item = item;
            this.normName = normName;
        }
    }
}
