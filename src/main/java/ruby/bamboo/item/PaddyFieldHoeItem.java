package ruby.bamboo.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlocks;

/**
 * 田んぼクワ (sakura PaddyFieldHoe の移植)。
 * <p>
 * HoeItem(Tiers.DIAMOND)相当、maxStack 1。
 * useOnでEventHooks.onHoeUse後にDIRT系をPADDY_FIELDへ変換、sound HOE_TILL。
 */
public class PaddyFieldHoeItem extends HoeItem {

    public PaddyFieldHoeItem(Properties props) {
        super(Tiers.DIAMOND, props.stacksTo(1).attributes(HoeItem.createAttributes(Tiers.DIAMOND, -3.0F, 0.0F)));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // 1.20.1では EventHooks.onHoeUseは削除。ToolAction経由に統合されたためフックは省略し直接変換する
        // 互換: BlockToolModificationEventは getToolModifiedState 内で発火される
        if (context.getClickedFace() != Direction.DOWN && level.isEmptyBlock(pos.above())) {
            BlockState state = level.getBlockState(pos);
            var block = state.getBlock();
            // sakura HOE_LOOKUP判定相当: 耕せる土系のみPADDY_FIELDへ変換
            boolean isTillable = block == Blocks.GRASS_BLOCK
                    || block == Blocks.DIRT
                    || block == Blocks.DIRT_PATH
                    || block == Blocks.COARSE_DIRT
                    || block == Blocks.ROOTED_DIRT
                    || block == Blocks.FARMLAND;
            // また、ForgeのToolActionでも判定 (他mod互換) — getToolModifiedStateが非nullなら耕せる
            if (!isTillable) {
                var toolState = state.getToolModifiedState(context, net.neoforged.neoforge.common.ItemAbilities.HOE_TILL, false);
                if (toolState != null) {
                    isTillable = true;
                }
            }
            if (isTillable) {
                var player = context.getPlayer();
                level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!level.isClientSide) {
                    level.setBlock(pos, BambooBlocks.PADDY_FIELD.get().defaultBlockState(), 11);
                    // sakura: level.setBlockState(pos, PADDY_FIELD.default)
                    level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos,
                            net.minecraft.world.level.gameevent.GameEvent.Context.of(player, BambooBlocks.PADDY_FIELD.get().defaultBlockState()));
                    if (player != null) {
                        context.getItemInHand().hurtAndBreak(1, player, context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }
}
