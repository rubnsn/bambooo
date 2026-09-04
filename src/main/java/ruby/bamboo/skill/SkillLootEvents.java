package ruby.bamboo.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.item.SkillBookItem;

/**
 * バニラのランダムチェスト (`minecraft:chests/*`) への激レア枠注入。
 * GlobalLootModifier は新規 DeferredRegister が要るため LootTableLoadEvent で追加する。
 * 箱あたり1%でスキル本13種・願いの杖のいずれか1個 (均等)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        for (RegistryObject<SkillBookItem> book : BambooItems.SKILL_BOOKS) {
            pool.add(LootItem.lootTableItem(book.get()).setWeight(1));
        }
        Item wand = BambooItems.WISH_WAND.get();
        if (wand != null) {
            pool.add(LootItem.lootTableItem(wand).setWeight(1));
        }
        event.getTable().addPool(pool.build());
    }
}
