package ruby.bamboo.item.katana;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.item.CommonKatana;

/**
 * 刀の独自攻撃 (旧 KatanaBase#onLeftClickEntity の移植)。
 * <p>
 * 1.20.1 には onLeftClickEntity 相当の Item フックが無いため
 * {@link AttackEntityEvent} で代替する:
 * <ul>
 * <li>刀を持っていればバニラ剣攻撃をキャンセルし、独自ダメージ
 * ({@link CommonKatana#getAttackDamage}) を与える</li>
 * <li>サーバー側で撃破成立を確認できたら {@link KatanaDropManager} で特殊ドロップ抽選</li>
 * <li>耐久は消費しない (旧仕様)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KatanaAttackHandler {

    private KatanaAttackHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CommonKatana)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        // バニラ剣の通常攻撃経路を無効化し、独自ダメージへ置換
        event.setCanceled(true);
        if (!player.level().isClientSide()) {
            float dmg = CommonKatana.getAttackDamage(target);
            DamageSource source = player.damageSources().playerAttack(player);
            boolean killed = target.hurt(source, dmg);

            if (killed && target.isDeadOrDying() && player.level() instanceof ServerLevel serverLevel) {
                ItemStack drop = KatanaDropManager.getRandomDropItem(serverLevel, target,
                        player.getRandom(), CommonKatana.getDropRate());
                if (drop != null) {
                    target.spawnAtLocation(drop);
                }
            }
        }
    }
}
