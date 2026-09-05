package ruby.bamboo.enchant;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooEnchantments;
import ruby.bamboo.item.NinjaBraceletItem;
import ruby.bamboo.item.katana.KatanaDropManager;

/**
 * 追い剥ぎ (pickpocket) ハンドラ。
 * 腕輪の pickpocket lv*10% でドロップ再抽選。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PickpocketHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;
        // killerはdamage sourceから取るが、ここでは攻撃者を event.getSource().getEntity() で取得
        var src = event.getSource().getEntity();
        if (!(src instanceof Player player)) return;

        // 1.21: Enchantment は datapack 化したため Holder 経由で取得する
        Holder<Enchantment> pickpocket = serverLevel.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(BambooEnchantments.PICKPOCKET);

        int lv = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() instanceof NinjaBraceletItem) {
                int cur = EnchantmentHelper.getItemEnchantmentLevel(pickpocket, s);
                if (cur > lv) lv = cur;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty() && s.getItem() instanceof NinjaBraceletItem) {
                int cur = EnchantmentHelper.getItemEnchantmentLevel(pickpocket, s);
                if (cur > lv) lv = cur;
            }
        }
        if (lv <= 0) return;
        float chance = lv * 0.1F;
        if (serverLevel.getRandom().nextFloat() >= chance) return;

        // 刀のドロップ表を流用: 対象EntityTypeのkatanaテーブルから1件抽選
        ItemStack extra = KatanaDropManager.getRandomDropItem(serverLevel, target, serverLevel.getRandom(), 0);
        if (extra != null && !extra.isEmpty()) {
            // ドロップ追加: event.getDrops() に EntityItem を追加
            var entityItem = new net.minecraft.world.entity.item.ItemEntity(serverLevel, target.getX(), target.getY(), target.getZ(), extra.copy());
            entityItem.setDefaultPickUpDelay();
            event.getDrops().add(entityItem);
        }
    }
}
