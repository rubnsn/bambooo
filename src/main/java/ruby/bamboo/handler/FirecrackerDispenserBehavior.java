package ruby.bamboo.handler;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import ruby.bamboo.BambooMod;
import ruby.bamboo.entity.FirecrackerEntity;
import ruby.bamboo.item.FirecrackerItem;

/**
 * かんしゃく玉のディスペンサー対応 (旧 DispenserBehaviorFireCracker の 1.20.1 移植)。
 * 5種すべてディスペンサーから発射できる。発射時点で着火済み。
 * <p>
 * 1.21 移植メモ (クラス内完結):
 * <ul>
 * <li>1.21 で {@code AbstractProjectileDispenseBehavior} は廃止され、
 * {@code ProjectileDispenseBehavior} ({@code ProjectileItem} 必須) に置き換わった。
 * アイテム側に手を入れずクラス内で完結させるため、
 * {@code DefaultDispenseItemBehavior#execute} を直接 override する
 * (初速 1.5・ぶれ 1.0 は旧 {@code getPower}/{@code getUncertainty} と同値)。</li>
 * <li>登録は本クラスの {@code @EventBusSubscriber(Bus.MOD)} 内で自己完結する。
 * 既存ファイル (BambooItems・BambooRecipes・ClientSetup 等) への追記は不要。</li>
 * <li>アイテム参照は {@code BuiltInRegistries} 解決のため、親が {@code BambooItems} に
 * 5種を登録した後に有効になる。未登録品は {@code instanceof} で読み飛ばす。</li>
 * </ul>
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class FirecrackerDispenserBehavior extends DefaultDispenseItemBehavior {

    /** 対応品の登録名 (BambooItems 側の登録名と一致させること) */
    private static final String[] TARGETS = {
            "firecracker_s", "firecracker_m", "firecracker_l",
            "firecracker_m_sticky", "firecracker_l_sticky"
    };

    private FirecrackerDispenserBehavior() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FirecrackerDispenserBehavior behavior = new FirecrackerDispenserBehavior();
            for (String name : TARGETS) {
                Item item = BuiltInRegistries.ITEM
                        .get(ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, name));
                if (item instanceof FirecrackerItem) {
                    DispenserBlock.registerBehavior(item, behavior);
                }
            }
        });
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        Level level = source.level();
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        Position pos = DispenserBlock.getDispensePosition(source);
        FirecrackerEntity entity = new FirecrackerEntity(level, pos.x(), pos.y(), pos.z(), stack);
        // 手投げと同じ初速 1.5・ぶれ 1.0 で発射 (旧 getPower/getUncertainty 相当)
        entity.shoot(facing.getStepX(), facing.getStepY(), facing.getStepZ(), 1.5F, 1.0F);
        level.addFreshEntity(entity);
        stack.shrink(1);
        return stack;
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
