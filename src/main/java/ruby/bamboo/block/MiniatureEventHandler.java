package ruby.bamboo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.MiniatureBlockEntity;

/**
 * ミニチュア外枠の誤破壊を汎用的に抑止する Forge イベントフック。
 * <p>
 * 旧来は {@code MiniatureBlock#getDestroyProgress / playerWillDestroy / onRemove} の
 * ThreadLocal フラグで外枠復元を試みていたが、クリエ/サバの長押しや予測破壊で
 * 外枠が消失するケースが残った。Forge の {@code BreakEvent} を併用することで
 * サーバ側での外枠破壊を確実にキャンセルし、汎用的に内部セルのみに破壊を限定する。
 * ンレッドストーン全オミットに伴い power 伝播の副作用も抑止。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class MiniatureEventHandler {

    // BreakSpeed は Block#getDestroyProgress で汎用的に処理するため、Forge 側では触らない。
    // 旧実装では innerProg に基づく速度補正を試みたが、getDestroyProgress と二重補正になり
    // サバイバルの破壊速度がバニラから乖離したため廃止 (2026-08-27)。

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof MiniatureBlock mb)) return;
        if (!(level.getBlockEntity(pos) instanceof MiniatureBlockEntity be)) return;
        if (be.isEmpty()) return;
        // 中身ありは外枠を絶対に壊さない。左クリックの内部破壊をここでバニラ準拠に実行し、外枠はキャンセル。
        // 唯一の外枠回収は MiniatureBlock#use のクワ右クリック (ALLOW_HOE_REMOVAL) のみ。
        // サバイバル長押しの内部破壊もこのイベントで処理することで、クライアント予測のチラつきを抑止する。
        Player player = event.getPlayer();
        if (player != null && !level.isClientSide) {
            // ヒット位置から対象セルを特定
            try {
                var hit = MiniatureBlock.getHitForPlayerStatic(player, level, pos);
                if (hit != null && hit.getBlockPos().equals(pos)) {
                    BlockPos targetPos = MiniatureBlock.calcHitPos(pos, hit.getLocation(), hit.getDirection().getOpposite(), be.getSize());
                    BlockState inner = be.getCell(targetPos);
                    if (inner.isAir()) {
                        BlockPos hitPos = MiniatureBlock.calcHitPos(pos, hit.getLocation(), hit.getDirection(), be.getSize());
                        if (be.isInRange(hitPos.getX(), hitPos.getY(), hitPos.getZ()) && !be.getCell(hitPos).isAir()) {
                            targetPos = hitPos;
                        } else {
                            targetPos = null;
                        }
                    }
                    if (targetPos != null) {
                        boolean isCreative = player.isCreative();
                        mb.breakInnerForAttack(be, targetPos, level, pos, player, !isCreative);
                    }
                }
            } catch (Exception e) {}
        }
        event.setCanceled(true);
    }
}
