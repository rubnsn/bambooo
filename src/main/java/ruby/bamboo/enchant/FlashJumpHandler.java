package ruby.bamboo.enchant;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooEnchantments;
import ruby.bamboo.item.NinjaBraceletItem;

/**
 * 閃跳 (flash_jump) ハンドラ。
 * 空中で1回のみ発動、着地でリセット、クール無し、耐久消費無し。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlashJumpHandler {

    private static final String TAG_FLASHED = "bamboomod:flash_used";
    private static final String TAG_AIR_TICKS = "bamboomod:flash_air_ticks";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return; // サーバのみで処理、クライアントは動きを補間
        if (player.isSpectator() || player.isCreative() && player.getAbilities().flying) return;

        // 着地でリセット
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying()) {
            player.getPersistentData().putBoolean(TAG_FLASHED, false);
            player.getPersistentData().putInt(TAG_AIR_TICKS, 0);
            return;
        }

        // 空中
        int airTicks = player.getPersistentData().getInt(TAG_AIR_TICKS) + 1;
        player.getPersistentData().putInt(TAG_AIR_TICKS, airTicks);

        if (player.getPersistentData().getBoolean(TAG_FLASHED)) return;

        // 腕輪所持チェック (インベントリ or 手持ち)
        if (!hasFlashJump(player)) return;

        // 発動条件: 空中に入ってから一定tick (5) 経過し、上昇中 or ジャンプ入力相当
        // サーバではキー入力を直接取れないため、Y速度が正 または 空中tickが一定で発動
        // WASD入力は look方向への加速として反映、入力がなければ真上へ
        Vec3 motion = player.getDeltaMovement();
        boolean isRising = motion.y > -0.1; // 落下直前でも発動可能にするため緩め
        if (airTicks < 5) return;
        // 地上から離れてから一度だけ発動: 上昇中 or 横移動中
        if (!isRising && motion.horizontalDistanceSqr() < 0.01) return;

        // 発動
        Vec3 look = player.getLookAngle();
        // 横入力の有無は motion から推定、look方向へ加速
        double pushX = look.x * 0.6;
        double pushZ = look.z * 0.6;
        double pushY = 0.4;
        // 既存速度に加算
        player.setDeltaMovement(motion.add(pushX, pushY, pushZ));
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.getPersistentData().putBoolean(TAG_FLASHED, true);
        // 音任意: エンダードラゴン羽ばたき等
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static boolean hasFlashJump(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() instanceof NinjaBraceletItem) {
                if (EnchantmentHelper.getItemEnchantmentLevel(BambooEnchantments.FLASH_JUMP.get(), s) > 0) return true;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty() && s.getItem() instanceof NinjaBraceletItem) {
                if (EnchantmentHelper.getItemEnchantmentLevel(BambooEnchantments.FLASH_JUMP.get(), s) > 0) return true;
            }
        }
        for (ItemStack s : player.getInventory().armor) {
            if (!s.isEmpty() && s.getItem() instanceof NinjaBraceletItem) {
                if (EnchantmentHelper.getItemEnchantmentLevel(BambooEnchantments.FLASH_JUMP.get(), s) > 0) return true;
            }
        }
        return false;
    }
}
