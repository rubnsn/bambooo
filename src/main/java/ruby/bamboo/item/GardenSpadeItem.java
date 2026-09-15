package ruby.bamboo.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import ruby.bamboo.BambooMod;

/**
 * 花壇用スコップ。
 * <p>
 * {@code PaddyFieldHoeItem} のスコップ版。バニラの土・草ブロックを右クリックで花壇へ変換する。
 * 耐久あり (IRON相当)、使用ごとに1消費。
 */
public class GardenSpadeItem extends ShovelItem {

    /** 変換先の花壇ブロック ID。親の BambooBlocks.FLOWER_BED 配線後に解決される。 */
    public static final ResourceLocation FLOWER_BED_ID = ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "flower_bed");

    public GardenSpadeItem(Properties props) {
        super(Tiers.IRON, props.stacksTo(1).attributes(ShovelItem.createAttributes(Tiers.IRON, 1.5F, -3.0F)));
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
                Block bed = BuiltInRegistries.BLOCK.get(FLOWER_BED_ID);
                if (bed == null || bed == Blocks.AIR) {
                    return InteractionResult.PASS;
                }
                BlockState bedState = bed.defaultBlockState();
                var player = context.getPlayer();
                level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!level.isClientSide) {
                    level.setBlock(pos, bedState, 11);
                    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, bedState));
                    if (player != null) {
                        context.getItemInHand().hurtAndBreak(1, player,
                                context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bamboomod.garden_spade").withStyle(ChatFormatting.AQUA));
    }
}
