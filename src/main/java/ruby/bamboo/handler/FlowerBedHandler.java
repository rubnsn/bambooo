package ruby.bamboo.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.BambooPotBlock;
import ruby.bamboo.block.FlowerBedBlock;
import ruby.bamboo.block.entity.FlowerBedBlockEntity;

/**
 * 花壇のシフト時スニークバイパス対策。
 * バニラの BlockItem はシフト中は Block#use をスキップして隣接に置こうとするため、
 * シフト+右クリック (グリッド配置) の要求が隣接設置に化ける。
 * ここで RightClickBlock を横取りし、花壇に空きがあれば同一ブロック内にグリッド配置する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlowerBedHandler {

    private FlowerBedHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FlowerBedBlock)) {
            return;
        }

        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (event.getHitVec().getDirection() != Direction.UP) {
            return;
        }

        ItemStack handStack = event.getItemStack();
        if (handStack.isEmpty()) {
            return;
        }
        if (!FlowerBedBlock.isValidPlant(handStack)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof FlowerBedBlockEntity bed)) {
            return;
        }
        if (bed.getPlantCount() >= FlowerBedBlockEntity.MAX_PLANTS) {
            return;
        }
        if (bed.getGridCount() >= FlowerBedBlockEntity.MAX_GRID) {
            return;
        }

        if (level.isClientSide) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(true));
            return;
        }

        double hitX = Mth.clamp(event.getHitVec().getLocation().x - pos.getX(), 0.0, 1.0);
        double hitZ = Mth.clamp(event.getHitVec().getLocation().z - pos.getZ(), 0.0, 1.0);
        float offsetX = FlowerBedBlock.gridSnap(hitX);
        float offsetZ = FlowerBedBlock.gridSnap(hitZ);
        boolean isCactus = handStack.is(net.minecraft.world.item.Items.CACTUS);
        float scale = (isCactus ? BambooPotBlock.GRID_SCALE_CACTUS : BambooPotBlock.GRID_SCALE) + (level.random.nextFloat() - 0.5f) * 0.02f;

        boolean ok = bed.addPlant(handStack, offsetX, offsetZ, scale, true);
        if (ok) {
            if (!player.isCreative()) {
                handStack.shrink(1);
            }
            boolean hasPlant = bed.getPlantCount() > 0;
            boolean cur = state.getValue(FlowerBedBlock.ATTACHED);
            if (cur != hasPlant) {
                level.setBlock(pos, state.setValue(FlowerBedBlock.ATTACHED, hasPlant), 3);
            } else {
                level.sendBlockUpdated(pos, state, state, 3);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(false));
        }
    }
}
