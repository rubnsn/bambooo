package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.CutBlockEntity;

/**
 * カットブロック外枠の誤破壊を汎用的に抑止する Forge イベントフック。
 * MiniatureEventHandler と同型。BreakEventで外枠をキャンセルし内部のみを破壊する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CutBlockEventHandler {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof CutBlock cutBlock)) return;
        if (!(level.getBlockEntity(pos) instanceof CutBlockEntity be)) return;
        if (be.isEmpty()) return;
        if (CutBlock.isRemovalAllowed()) return;
        Player player = event.getPlayer();
        if (player != null && !level.isClientSide) {
            try {
                var hit = CutBlock.getHitForPlayerStatic(player, level, pos);
                if (hit != null && hit.getBlockPos().equals(pos)) {
                    CutBlockEntity.CutEntry target = CutBlock.findHitEntry(be, pos, hit);
                    if (target != null) {
                        boolean isCreative = player.isCreative();
                        cutBlock.breakInnerForAttack(be, target, level, pos, player, !isCreative);
                    }
                }
            } catch (Exception e) {}
        }
        event.setCanceled(true);
    }
}
