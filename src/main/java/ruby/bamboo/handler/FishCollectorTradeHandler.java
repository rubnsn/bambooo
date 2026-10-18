package ruby.bamboo.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.fishing.FishSize;
import ruby.bamboo.core.fishing.FishingEntry;
import ruby.bamboo.core.fishing.FishingManager;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.core.init.BambooVillagers;

/**
 * 魚コレクター (職業ブロック: 魚捕り籠) の取引。
 * <ul>
 * <li>各レベル1枠 (Lv1=1種、Lv5=5種)。レベルアップで取引可能な魚種が1つ増える</li>
 * <li>基礎レート: 金品質魚4匹 → ガチャコイン1枚。各枠1日1取引 (maxUses=1)</li>
 * <li>魚種は釣りテーブル (FishingManager) の FISH カテゴリから毎回ランダム。
 * 重複OK (cod が2枠に並ぶ等)。ゴミ・宝箱はランクが付かないため除外。
 * 他 mod がテーブルへ魚を追加すれば自動でプールに入る</li>
 * <li>日替わり: サーバーの日付が変わったら全枠を再抽選する (下記 LevelTick 参照)。
 * バニラの restock は uses しか戻さないため、魚種の入れ替えは自前で行う</li>
 * </ul>
 * <p>
 * 金品質の要求は NBT (`bamboo_size_class=gold`) で行う。
 * MerchantOffer.satisfiedBy は cost 側にタグがある場合のみタグ一致を要求するため、
 * 金タグ付き cost は金魚でしか満たせない。GUI の等級表示は FishingTooltipHandler が出す。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FishCollectorTradeHandler {

    /** 基礎レート: 金魚この匹数 → ガチャコイン1枚 (全レベル共通) */
    public static final int FISH_COST = 4;
    /** 各枠1日1取引 */
    public static final int MAX_USES = 1;
    /** 日替わり再抽選の刻印 (Villager の永続 NBT。日番号を保持) */
    private static final String TAG_TRADE_DAY = "bamboomod:fish_trade_day";
    /** 日替わりチェックの間隔 (tick)。常時スキャンしない */
    private static final int CHECK_INTERVAL = 100;

    private FishCollectorTradeHandler() {
    }

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (!BambooVillagers.FISH_COLLECTOR.isPresent()) return;
        if (event.getType() != BambooVillagers.FISH_COLLECTOR.get()) {
            return;
        }
        // 各レベル1枠。レベルアップ (updateTrades) 時に各枠の魚が1回抽選される。
        // 日替わりの入れ替えは onLevelTick が翌日以降に行う
        for (int level = 1; level <= 5; level++) {
            final int lv = level;
            event.getTrades().get(lv).add((trader, rand) -> makeOffer(rand, lv));
        }
    }

    /**
     * 日替わり再抽選。オーバーワールドの日番号が刻印と異なる
     * 魚コレクターの全取引枠を作り直す (枠数=現在の村人レベル)。
     * 取引 GUI を開いている最中の村人は次回に回す (クライアントとずれるため)。
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % CHECK_INTERVAL != 0) return;
        if (!BambooVillagers.FISH_COLLECTOR.isPresent()) return;

        long today = level.getDayTime() / 24000L;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Villager villager)) continue;
            if (villager.getVillagerData().getProfession() != BambooVillagers.FISH_COLLECTOR.get()) continue;
            if (villager.getTradingPlayer() != null) continue;
            if (villager.getPersistentData().getLong(TAG_TRADE_DAY) == today
                    && villager.getPersistentData().contains(TAG_TRADE_DAY)) {
                continue;
            }
            rerollOffers(villager, today);
        }
    }

    /** 全枠を作り直す。枠数=村人レベル (1-5)。uses は 0 からの新しい1日分 */
    private static void rerollOffers(Villager villager, long today) {
        int slots = Mth.clamp(villager.getVillagerData().getLevel(), 1, 5);
        RandomSource rand = villager.getRandom();
        MerchantOffers fresh = new MerchantOffers();
        for (int i = 1; i <= slots; i++) {
            MerchantOffer offer = makeOffer(rand, i);
            if (offer != null) {
                fresh.add(offer);
            }
        }
        // 万が一プール空振りで0枠になっても空リストでは上書きしない (取引消失防止)
        if (fresh.isEmpty()) return;
        villager.setOffers(fresh);
        villager.getPersistentData().putLong(TAG_TRADE_DAY, today);
    }

    /** 金魚 FISH_COST 匹 → ガチャコイン1枚。魚種は毎回ランダム (重複あり) */
    private static MerchantOffer makeOffer(RandomSource rand, int level) {
        Item fish = randomFishItem(rand);
        ItemStack cost = new ItemStack(fish, FISH_COST);
        cost.getOrCreateTag().putString(FishSize.TAG_KEY, FishSize.GOLD.tagValue);
        ItemStack result = new ItemStack(BambooItems.GACHA_COIN.get(), 1);
        // xp はレベル×10 (閾値 10/70/150/250 に対し、1日1取引でも育つ配分)。
        // demand 係数は asobinin と同じ 0.05F
        return new MerchantOffer(cost, result, MAX_USES, 10, 0.05F);
    }

    /**
     * 釣りテーブルの FISH カテゴリから1種をランダム取得。
     * 他 mod による追加魚も自動追従する。プールが空ならタラにフォールバックする。
     */
    private static Item randomFishItem(RandomSource rand) {
        List<Item> pool = new ArrayList<>();
        for (FishingEntry entry : FishingManager.getEntries()) {
            if (!entry.isFish()) continue;
            Item item = ForgeRegistries.ITEMS.getValue(entry.itemId);
            if (item != null && item != Items.AIR) {
                pool.add(item);
            }
        }
        if (pool.isEmpty()) {
            return Items.COD;
        }
        return pool.get(rand.nextInt(pool.size()));
    }
}
