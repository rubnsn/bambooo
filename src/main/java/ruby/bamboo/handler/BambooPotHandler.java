package ruby.bamboo.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.BambooPotBlock;
import ruby.bamboo.block.entity.BambooPotBlockEntity;

/**
 * 竹鉢のシフト時スニークバイパス対策。
 * バニラの BlockItem はシフト中は Block#use をスキップして隣接に置こうとするため、
 * シフト+右クリック（グリッド9個）の要求が隣接設置に化ける。
 * ここで RightClickBlock を横取りし、鉢に空きがあれば同一ブロック内にグリッド配置する。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BambooPotHandler {

    private BambooPotHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BambooPotBlock)) return;

        Player player = event.getEntity();
        if (player == null) return;
        if (!player.isShiftKeyDown()) return;
        if (event.getHitVec().getDirection() != Direction.UP) return;

        ItemStack handStack = event.getItemStack();
        if (handStack.isEmpty()) return;

        // 固定形状のため鉢自体の追加配置はなし。花のみ横取り（シフト時のグリッド配置）
        boolean isPlant = BambooPotBlock.isValidPlant(handStack);
        if (!isPlant) return;
        if (!(level.getBlockEntity(pos) instanceof BambooPotBlockEntity pot)) return;

        // 花のグリッド 9本まで
        if (pot.getPlantCount() >= BambooPotBlockEntity.MAX_PLANTS) return;
        if (pot.getGridCount() >= BambooPotBlockEntity.MAX_GRID) return;

        if (level.isClientSide) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(true));
            return;
        }

        double hitX = Mth.clamp(event.getHitVec().getLocation().x - pos.getX(), 0.0, 1.0);
        double hitZ = Mth.clamp(event.getHitVec().getLocation().z - pos.getZ(), 0.0, 1.0);
        Direction facing = state.getValue(BambooPotBlock.FACING);
        float worldOffX = (float) hitX - 0.5f;
        float worldOffZ = (float) hitZ - 0.5f;
        float[] localHit = BambooPotBlock.worldToLocal(worldOffX, worldOffZ, facing);
        float rawLX = localHit[0];
        float offsetLX = Mth.clamp(Math.round(rawLX * 8f) / 8f, -0.375f, 0.375f);
        float offsetLZ = 0f; // 中央線
        boolean isCactus = handStack.is(net.minecraft.world.item.Items.CACTUS);
        float scale = (isCactus ? BambooPotBlock.GRID_SCALE_CACTUS : BambooPotBlock.GRID_SCALE) + (level.random.nextFloat() - 0.5f) * 0.02f;
        float offsetX = offsetLX;
        float offsetZ = offsetLZ;

        boolean ok = pot.addPlant(handStack, offsetX, offsetZ, scale, true);
        if (ok) {
            if (!player.isCreative()) handStack.shrink(1);
            boolean hasPlant = pot.getPlantCount() > 0;
            boolean cur = state.getValue(BambooPotBlock.ATTACHED);
            if (cur != hasPlant) {
                level.setBlock(pos, state.setValue(BambooPotBlock.ATTACHED, hasPlant), 3);
            } else {
                level.sendBlockUpdated(pos, state, state, 3);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(false));
        }
    }
}
