package ruby.bamboo.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 動的エンティティ名検索（願いにモンスター/動物の名前が直接書かれた場合の召喚用）。
 * 表示名ベースのため、日本語の一般名は JSON エントリ（bamboo_wish/mob_*.json）側が担う。
 * ここではレジストリの表示名（英語等）との一致を扱う。
 */
public final class WishEntitySearch {

    private WishEntitySearch() {
    }

    public static EntityType<?> find(String normalizedQuery, ServerLevel level) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return null;
        }
        List<EntityType<?>> exact = new ArrayList<>();
        List<TypeWithName> partials = new ArrayList<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!isSummonableCandidate(type)) {
                continue;
            }
            String display = type.getDescription().getString();
            if (display == null || display.isEmpty()) {
                continue;
            }
            String norm = WishNormalizer.normalize(display);
            if (norm.isEmpty()) {
                continue;
            }
            if (norm.equals(normalizedQuery)) {
                exact.add(type);
            } else if (norm.contains(normalizedQuery)) {
                partials.add(new TypeWithName(type, norm));
            }
        }

        List<EntityType<?>> chosen;
        if (!exact.isEmpty()) {
            exact.sort(Comparator.comparingInt(WishEntitySearch::namespacePriority)
                    .thenComparingInt(WishEntitySearch::registryNameLength)
                    .thenComparing(WishEntitySearch::registryName));
            chosen = exact;
        } else if (!partials.isEmpty()) {
            int bestDist = Integer.MAX_VALUE;
            List<EntityType<?>> best = new ArrayList<>();
            for (TypeWithName p : partials) {
                int dist = levenshtein(normalizedQuery, p.normName);
                if (dist < bestDist) {
                    bestDist = dist;
                    best.clear();
                    best.add(p.type);
                } else if (dist == bestDist) {
                    best.add(p.type);
                }
            }
            best.sort(Comparator.comparingInt(WishEntitySearch::namespacePriority)
                    .thenComparingInt(WishEntitySearch::registryNameLength)
                    .thenComparing(WishEntitySearch::registryName));
            chosen = best;
        } else {
            return null;
        }

        // 実際に生成でき LivingEntity である最初の候補を返す（幻影/painting 等の誤爆防止）
        for (EntityType<?> type : chosen) {
            try {
                Entity e = type.create(level);
                if (e instanceof LivingEntity) {
                    return type;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 生成してよいカテゴリか（MISC=アイテム/矢/エフェクト雲等は除外、プレイヤー型も除外） */
    private static boolean isSummonableCandidate(EntityType<?> type) {
        if (type == EntityType.PLAYER) return false;
        return type.getCategory() != MobCategory.MISC;
    }

    private static int namespacePriority(EntityType<?> type) {
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (rl == null) return 2;
        String ns = rl.getNamespace();
        if ("minecraft".equals(ns)) return 0;
        if ("bamboomod".equals(ns)) return 1;
        return 2;
    }

    private static int registryNameLength(EntityType<?> type) {
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return rl == null ? Integer.MAX_VALUE : rl.getPath().length();
    }

    private static String registryName(EntityType<?> type) {
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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

    private static class TypeWithName {
        final EntityType<?> type;
        final String normName;

        TypeWithName(EntityType<?> type, String normName) {
            this.type = type;
            this.normName = normName;
        }
    }
}
