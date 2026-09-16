package ruby.bamboo.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;

/**
 * バニラのランダムチェスト (`minecraft:chests/*`) への激レア枠注入。
 * GlobalLootModifier は新規 DeferredRegister が要るため LootTableLoadEvent で追加する。
 * 箱あたり1%でスキル本13種・願いの杖のいずれか1個 (均等)。
 *
 * <p>1.21.1 NeoForge: スキル本13種は {@code BambooItems} 未配線でも組めるよう
 * レジストリ参照 (親が登録したら解決される。未登録の本は枠から除外)。
 * 願いの杖は worktree 側に {@code BambooItems.WISH_WAND} が存在するため直接参照。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SkillLootEvents {

    private SkillLootEvents() {
    }

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!"minecraft".equals(name.getNamespace()) || !name.getPath().startsWith("chests/")) {
            return;
        }
        LootPool.Builder pool = LootPool.lootPool()
                .name("bamboomod:skill_book_rare")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(0.01F));
        for (SkillType type : SkillType.values()) {
            Item book = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID,
                    "skill_book_" + type.getId()));
            if (book == null || book == Items.AIR) {
                continue;
            }
            pool.add(LootItem.lootTableItem(book).setWeight(1));
        }
        Item wand = BambooItems.WISH_WAND.get();
        if (wand != null && wand != Items.AIR) {
            pool.add(LootItem.lootTableItem(wand).setWeight(1));
        }
        event.getTable().addPool(pool.build());
    }
}
