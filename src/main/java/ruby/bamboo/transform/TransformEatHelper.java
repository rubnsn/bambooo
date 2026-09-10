package ruby.bamboo.transform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 変身中の非食料摂取 (長押し食べ) の共有定義。
 * サーバは startUsingItem + Start(32tick) + Finish(効果) + Stop(破棄)。
 * クライアントは即時開始 + duration のみ (効果はサーバ)。
 * 口元モーションは client Mixin が EAT 扱いにする (pending がある間のみ)。
 */
public final class TransformEatHelper {

    /** 食料相当の長押し時間 (バニラ食料準拠)。 */
    public static final int EAT_DURATION = 32;

    public record EatDef(int nutrition, float saturation, float heal, boolean honeyRoll) {
    }

    public record Pending(InteractionHand hand, Item item, EatDef def) {
    }

    /** サーバ側の pending (サーバスレッドのみ触る)。 */
    private static final Map<UUID, Pending> SERVER = new ConcurrentHashMap<>();

    private TransformEatHelper() {
    }

    /**
     * 変身IDと手持ちから摂取定義を引く。対象外は null。
     * ゴーレム系は要回復時のみ対象 (満タン時は通常クリック扱い)。
     */
    public static EatDef match(String entityId, ItemStack stack, Player player) {
        if (entityId == null || entityId.isEmpty() || stack.isEmpty()) {
            return null;
        }
        // オウム・ニワトリ: 種
        if ((entityId.equals("minecraft:parrot") || entityId.equals("minecraft:chicken"))
                && isSeedItem(stack)) {
            return new EatDef(1, 0.2F, 0F, false);
        }
        // ウシ・ヤギ: 草花
        if ((entityId.equals("minecraft:cow") || entityId.equals("minecraft:goat"))
                && isPlantItem(stack)) {
            return new EatDef(1, 0.3F, 0F, false);
        }
        // パンダ: 竹
        if (entityId.equals("minecraft:panda") && stack.is(Items.BAMBOO)) {
            return new EatDef(2, 0.4F, 0F, false);
        }
        // ゴーレム系: 金属・雪食い (要回復)
        if (entityId.equals("minecraft:iron_golem") && player.getHealth() < player.getMaxHealth()
                && (stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT)
                        || stack.is(Items.GOLD_INGOT))) {
            return new EatDef(0, 0F, 4.0F, false);
        }
        if (entityId.equals("minecraft:snow_golem") && player.getHealth() < player.getMaxHealth()
                && stack.is(Items.SNOW_BLOCK)) {
            return new EatDef(0, 0F, 4.0F, false);
        }
        // ハチ: 花 (+空ビンがあれば10%で蜂蜜)
        if (entityId.equals("minecraft:bee") && stack.is(net.minecraft.tags.ItemTags.FLOWERS)) {
            return new EatDef(2, 0.5F, 0F, true);
        }
        return null;
    }

    public static boolean isSeedItem(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.MELON_SEEDS)
                || stack.is(Items.PUMPKIN_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.TORCHFLOWER_SEEDS);
    }

    public static boolean isPlantItem(ItemStack stack) {
        if (stack.is(net.minecraft.tags.ItemTags.FLOWERS)) {
            return true;
        }
        return stack.is(Items.GRASS)
                || stack.is(Items.TALL_GRASS)
                || stack.is(Items.FERN)
                || stack.is(Items.LARGE_FERN);
    }

    public static void serverPut(Player player, InteractionHand hand, ItemStack stack, EatDef def) {
        SERVER.put(player.getUUID(), new Pending(hand, stack.getItem(), def));
    }

    public static Pending serverPeek(Player player) {
        return SERVER.get(player.getUUID());
    }

    public static Pending serverTake(Player player) {
        return SERVER.remove(player.getUUID());
    }

    public static void serverClear(Player player) {
        SERVER.remove(player.getUUID());
    }
}
