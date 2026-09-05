package ruby.bamboo.item.katana;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;

/**
 * 居合カウンター (旧 CounterManager の移植)。
 * <p>
 * 2026-08-23 鈎縄統合によりOMIT: 刀右クリックは鈎縄にリプレイスされたためカウンターは無効化。
 * ファイルはアーカイブとして保持、イベント登録は停止。
 */
public final class CounterManager {

    private CounterManager() {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // カウンター受付中か (旧 getCooldown > 0.9F)
        if (player.getCooldowns().getCooldownPercent(BambooItems.COMMON_KATANA.get(), 0F) <= 0.9F) {
            return;
        }
        DamageSource source = event.getSource();
        var direct = source.getDirectEntity();
        if (direct == null) {
            return;
        }

        // ===== 矢反射 =====
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            event.setCanceled(true);
            if (direct instanceof AbstractArrow arrow) {
                arrow.setOwner(player);
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
            }
            var motion = direct.getDeltaMovement();
            direct.setDeltaMovement(motion.scale(10));
            return;
        }

        // ===== 近接反撃 (player / mob 由来のみ。落下/火/爆発等は対象外) =====
        var attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && (source.getMsgId().equals("player")
                || source.getMsgId().equals("mob"))) {
            event.setCanceled(true);
            living.invulnerableTime = 0;
            DamageSource magic = living.damageSources().magic();
            if (living.hurt(magic, event.getAmount())) {
                double dx = player.getX() - living.getX();
                double dz = player.getZ() - living.getZ();
                living.knockback(0.5F, dx, dz);
                living.invulnerableTime = 0;
            }
        }
    }
}
