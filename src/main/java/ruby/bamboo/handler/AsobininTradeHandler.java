package ruby.bamboo.handler;

import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooBlocks;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooVillagers;

/**
 * 遊び人の取引。
 * <ul>
 * <li>Lv1: 竹 → 竹おにぎり・竹飯など mod 食料中心</li>
 * <li>Lv2-4: 竹 → 団子・丼・麺類 (適当枠)</li>
 * <li>Lv5: バンブーエキス64 → ガチャコイン・gem の目玉4種。
 * 村人ごとに2種ランダム提示 (バニラと同一仕組み)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AsobininTradeHandler {

    private AsobininTradeHandler() {
    }

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != BambooVillagers.ASOBININ.get()) {
            return;
        }
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        Item extract = BambooItems.BAMBOO_EXTRACT.get();
        // Lv1: 竹 → mod 食料
        trades.get(1).add((trader, rand) -> offer(extract, 2, food("takeoni"), 2, 12, 2));
        trades.get(1).add((trader, rand) -> offer(extract, 2, food("takemesi"), 1, 12, 2));
        // Lv1: エメラルド → たけのこ (基本素材のバニラ手段)
        trades.get(1).add((trader, rand) -> offer(Items.EMERALD, 1,
                BambooBlocks.BAMBOO_SHOOT.get().asItem(), 1, 12, 2));
        // Lv2-4: 竹 → 団子・丼・麺類
        trades.get(2).add((trader, rand) -> offer(extract, 3, food("dananko"), 2, 12, 10));
        trades.get(2).add((trader, rand) -> offer(extract, 3, food("kinooni"), 2, 12, 10));
        trades.get(3).add((trader, rand) -> offer(extract, 3, food("oyako"), 1, 12, 20));
        trades.get(3).add((trader, rand) -> offer(extract, 3, food("sakuramochi"), 1, 12, 20));
        trades.get(4).add((trader, rand) -> offer(extract, 4, food("katsudon"), 1, 12, 25));
        trades.get(4).add((trader, rand) -> offer(extract, 4, food("ramen"), 1, 12, 25));
        // Lv5 目玉: ガチャコインの入手
        trades.get(5).add((trader, rand) -> offer(extract, 64, BambooItems.GACHA_COIN.get(), 1, 2, 30));
        trades.get(5).add((trader, rand) -> offer(BambooItems.SAKURA_GEM.get(), 2, BambooItems.GACHA_COIN.get(), 1, 2, 30));
        trades.get(5).add((trader, rand) -> offer(BambooItems.GINKGO_GEM.get(), 2, BambooItems.GACHA_COIN.get(), 1, 2, 30));
        trades.get(5).add((trader, rand) -> offer(BambooItems.MAPLE_GEM.get(), 2, BambooItems.GACHA_COIN.get(), 1, 2, 30));
    }

    private static MerchantOffer offer(Item cost, int costCount, Item result, int resultCount, int maxUses,
            int xp) {
        return new MerchantOffer(new ItemStack(cost, costCount), new ItemStack(result, resultCount), maxUses,
                xp, 0.05F);
    }

    private static Item food(String texName) {
        Item item = ForgeRegistries.ITEMS
                .getValue(new ResourceLocation(BambooMod.MODID, "bamboofood_" + texName));
        if (item == null) {
            throw new IllegalStateException("missing food bamboomod:bamboofood_" + texName);
        }
        return item;
    }
}
