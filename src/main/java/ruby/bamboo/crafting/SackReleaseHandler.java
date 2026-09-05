package ruby.bamboo.crafting;

import java.util.List;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;
import ruby.bamboo.item.Sack;

/**
 * 袋のクラフト排出 (旧 V キー release の代替)。
 * <p>
 * 内容のある袋をクラフトグリッドに置いて何かをクラフトすると、
 * 中身が全て実体化ドロップされ、袋は空になって戻る。
 * <p>
 * 実装: {@link PlayerEvent.ItemCraftedEvent} (Forge バス) を購読し、
 * クラフトマトリクス内の袋から中身を吐き出す。
 * レシピ自体は data/bamboomod/recipes/sack_release.json (shapeless: sack → 空 sack)
 * を使用するが、本ハンドラは任意のレシピで袋が素材に入っていれば発火する。
 * <p>
 * 旧仕様 (1.5節): 「キー連携は行わず、このアイテム単品でクラフト→中身を排出」
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SackReleaseHandler {

    private SackReleaseHandler() {
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        // サーバー側のみ
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        var grid = event.getInventory();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (!(stack.getItem() instanceof Sack)) {
                continue;
            }
            if (!Sack.hasContent(stack)) {
                continue;
            }
            // 中身を全て実体化ドロップし、袋を空にする
            List<ItemStack> drops = Sack.releaseAll(event.getEntity().level(), event.getEntity(), stack);
            for (ItemStack drop : drops) {
                ItemEntity entity = new ItemEntity(event.getEntity().level(),
                        event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(), drop);
                event.getEntity().level().addFreshEntity(entity);
            }
            if (!drops.isEmpty()) {
                grid.setItem(i, stack); // NBT 更新を反映
            }
        }
    }
}
