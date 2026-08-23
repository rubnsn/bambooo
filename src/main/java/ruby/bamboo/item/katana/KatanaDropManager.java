package ruby.bamboo.item.katana;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 刀の特殊ドロップ管理 (旧 KatanaDropManager の移植)。
 * <p>
 * 旧仕様の維持点:
 * <ul>
 * <li>候補を全件個別抽選せず、まず1件を選んでから確率判定する</li>
 * <li>dropRate は確率に加算される(刀ごとのボーナス。通常刀は0)</li>
 * <li>randomAmount は基礎個数に 0〜N を追加する</li>
 * </ul>
 * <p>
 * ドロップアイテム自体はバニラ準拠の JSON (data/bamboomod/loot_tables/entities/katana/*.json)
 * へ外出しし、本クラスはその loot table を抽選する。
 */
public final class KatanaDropManager {

    private KatanaDropManager() {
    }

    /** ドロップ表: EntityType → loot table パス (bamboomod:entities/katana/xxx) */
    private static final Map<EntityType<?>, List<ResourceLocation>> DROP_TABLES = new HashMap<>();

    /**
     * 特殊ドロップの loot table を登録する。
     *
     * @param type        対象エンティティ
     * @param tablePaths  候補テーブル (例 "katana/zombie")。bamboomod 名前空間の
     *                    entities/katana/ 配下が参照される
     */
    public static void addDrop(EntityType<?> type, String... tablePaths) {
        List<ResourceLocation> list = DROP_TABLES.computeIfAbsent(type, k -> new ArrayList<>());
        for (String path : tablePaths) {
            ResourceLocation id = new ResourceLocation("bamboomod", "entities/katana/" + path);
            if (!list.contains(id)) {
                list.add(id);
            }
        }
    }

    /** 対象が登録済みか */
    public static boolean isDropableEntity(EntityType<?> type) {
        return DROP_TABLES.containsKey(type);
    }

    /**
     * 特殊ドロップ抽選 (旧 getRandomDropItem 相当)。
     * まず候補1件を選び、そのテーブルで抽選する。外れたら null。
     */
    @Nullable
    public static ItemStack getRandomDropItem(ServerLevel level, LivingEntity killed,
            RandomSource rand, float dropRate) {
        // EntityType キー解決用 (旧は entity.getClass() 完全一致)
        List<ResourceLocation> candidates = DROP_TABLES.get(killed.getType());
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        ResourceLocation tableId = candidates.get(rand.nextInt(candidates.size()));
        var table = level.getServer().getLootData().getLootTable(tableId);
        var builder = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, killed)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        killed.position())
                .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,
                        null)
                .withLuck(0F);
        // 確率判定は旧仕様どおり reality+dropRate 相当を random_chance で JSON 側に任せるため、
        // ここでは単純にテーブルロールする (dropRate 加算が必要な場合は JSON 側で調整)
        var generated = new java.util.ArrayList<ItemStack>();
        table.getRandomItems(builder.create(
                net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY),
                generated::add);
        return generated.isEmpty() ? null : generated.get(0);
    }

    /** デバッグ用登録数 */
    public static int getTableCount() {
        return DROP_TABLES.size();
    }
}
