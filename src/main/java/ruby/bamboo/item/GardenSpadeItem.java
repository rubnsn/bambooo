package ruby.bamboo.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ruby.bamboo.core.init.BambooBlocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 花壇用スコップ。
 * <p>
 * {@code PaddyFieldHoeItem} のスコップ版。バニラの土・草ブロックを右クリックで花壇へ変換する。
 * 耐久あり (IRON相当)、使用ごとに1消費。
 */
public class GardenSpadeItem extends ShovelItem {

    public GardenSpadeItem(Properties props) {
        super(Tiers.IRON, 1.5F, -3.0F, props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (context.getClickedFace() != Direction.DOWN && level.isEmptyBlock(pos.above())) {
            BlockState state = level.getBlockState(pos);
            var block = state.getBlock();
            // 要求通りバニラの土・草のみ変換
            boolean convertible = block == Blocks.GRASS_BLOCK
                    || block == Blocks.DIRT;
            if (convertible) {
                var player = context.getPlayer();
                level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!level.isClientSide) {
                    level.setBlock(pos, BambooBlocks.FLOWER_BED.get().defaultBlockState(), 11);
                    level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, pos,
                            net.minecraft.world.level.gameevent.GameEvent.Context.of(player, BambooBlocks.FLOWER_BED.get().defaultBlockState()));
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
        tooltip.add(Component.translatable("tooltip.bamboomod.garden_spade").withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
