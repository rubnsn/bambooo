package ruby.bamboo.handler;

import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooItems;
import ruby.bamboo.entity.FirecrackerEntity;
import ruby.bamboo.item.FirecrackerItem;

/**
 * かんしゃく玉のディスペンサー対応 (旧 DispenserBehaviorFireCracker の 1.20.1 移植)。
 * 5種すべてディスペンサーから発射できる。発射時点で着火済み。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FirecrackerDispenserBehavior extends AbstractProjectileDispenseBehavior {

    private FirecrackerDispenserBehavior() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FirecrackerDispenserBehavior behavior = new FirecrackerDispenserBehavior();
            DispenserBlock.registerBehavior(BambooItems.FIRECRACKER_S.get(), behavior);
            DispenserBlock.registerBehavior(BambooItems.FIRECRACKER_M.get(), behavior);
            DispenserBlock.registerBehavior(BambooItems.FIRECRACKER_L.get(), behavior);
            DispenserBlock.registerBehavior(BambooItems.FIRECRACKER_M_STICKY.get(), behavior);
            DispenserBlock.registerBehavior(BambooItems.FIRECRACKER_L_STICKY.get(), behavior);
        });
    }

    @Override
    protected Projectile getProjectile(Level level, Position pos, ItemStack stack) {
        FirecrackerEntity entity = new FirecrackerEntity(level, pos.x(), pos.y(), pos.z(), stack);
        entity.setItem(stack.copyWithCount(1));
        return entity;
    }

    @Override
    protected float getUncertainty() {
        return 1.0F;
    }

    @Override
    protected float getPower() {
        // 手投げ (1.5) と同じ初速
        return 1.5F;
    }

    /** アイテム側の参照用 (型判定は ItemStack から行うため未使用だが旧版互換として残す) */
    @SuppressWarnings("unused")
    public static int getExplodeLv(ItemStack stack) {
        if (stack.getItem() instanceof FirecrackerItem item) {
            return item.getType().id;
        }
        return 0;
    }
}
