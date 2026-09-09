package ruby.bamboo.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ToolActions;
import ruby.bamboo.core.init.BambooBlocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 田んぼクワ (sakura PaddyFieldHoe の移植)。
 * <p>
 * HoeItem(Tiers.DIAMOND)相当、maxStack 1。
 * useOnでForgeEventFactory.onHoeUse後にDIRT系をPADDY_FIELDへ変換、sound HOE_TILL。
 */
public class PaddyFieldHoeItem extends HoeItem {

    public PaddyFieldHoeItem(Properties props) {
        super(Tiers.DIAMOND, -3, 0.0F, props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // 1.20.1では ForgeEventFactory.onHoeUseは削除。ToolAction経由に統合されたためフックは省略し直接変換する
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
                var toolState = state.getToolModifiedState(context, ToolActions.HOE_TILL, false);
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
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                            GameEvent.Context.of(player, BambooBlocks.PADDY_FIELD.get().defaultBlockState()));
                    if (player != null) {
                        context.getItemInHand().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(context.getHand()));
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.paddy_field_hoe").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.bamboomod.paddy_field.bucket").withStyle(ChatFormatting.AQUA));
    }
}
