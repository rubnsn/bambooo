package ruby.bamboo.item;

import java.util.function.Consumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ruby.bamboo.block.entity.CutBlockEntity;
import ruby.bamboo.client.renderer.CutBlockItemRenderer;

/**
 * カットブロックの BlockItem。
 * 表示名を "木材 (16×8)" のように動的に生成。同一サイズはスタック可。
 * インベントリでは BEWLR で原料ブロックのテクスチャを Bounds にクリップして描画する。
 */
public class CutBlockItem extends BlockItem {

    public CutBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.level.Level level = context.getLevel();
        net.minecraft.core.BlockPos clickedPos = context.getClickedPos();
        net.minecraft.core.Direction clickedFace = context.getClickedFace();
        net.minecraft.world.phys.Vec3 hitVec = context.getClickLocation();
        net.minecraft.world.entity.player.Player player = context.getPlayer();
        net.minecraft.world.item.ItemStack stack = context.getItemInHand();
        if (stack.isEmpty()) return super.useOn(context);
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir()) {
            // 空のcut_blockは通常のBlockItemとして配置を試みる（通常は失敗）
            return super.useOn(context);
        }
        byte yLevel = data.yLevel();
        byte hLevel = data.hLevel();
        // FACINGはプレイヤー向きの反対（CutBlock#getStateForPlacementと同様）
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        try {
            facing = context.getHorizontalDirection().getOpposite();
            if (facing.getAxis() == net.minecraft.core.Direction.Axis.Y) facing = net.minecraft.core.Direction.NORTH;
        } catch (Exception e) {
            facing = net.minecraft.core.Direction.NORTH;
        }

        // 1) クリックしたブロックが既存cut_blockなら、その隙間に充填を試みる（ヒット位置に最も近い空きサブ空間を探索）
        if (level.getBlockEntity(clickedPos) instanceof CutBlockEntity existingBe) {
            if (!existingBe.isEmpty()) {
                int[] bounds = existingBe.findBestBoundsForPlacement(hitVec, clickedPos, yLevel, hLevel, facing, clickedFace);
                if (bounds != null && existingBe.canAddEntry(bounds)) {
                    if (!level.isClientSide) {
                        existingBe.addEntry(data.state(), bounds);
                        if (player == null || !player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        level.sendBlockUpdated(clickedPos, existingBe.getBlockState(), existingBe.getBlockState(), 3);
                        level.playSound(null, clickedPos, existingBe.getBlockState().getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        // 2) 新規配置: 隣接位置
        net.minecraft.core.BlockPos placePos = clickedPos.relative(clickedFace);
        net.minecraft.world.level.block.state.BlockState placeState = level.getBlockState(placePos);
        boolean canReplace = placeState.canBeReplaced(new net.minecraft.world.item.context.BlockPlaceContext(context));
        // 既存cut_blockが空きを持つ場合も隙間として扱う（placePosがcut_blockで空きがあれば充填）
        if (level.getBlockEntity(placePos) instanceof CutBlockEntity placeBe) {
            if (!placeBe.isEmpty()) {
                int[] bounds = placeBe.findBestBoundsForPlacement(hitVec, placePos, yLevel, hLevel, facing, clickedFace);
                if (bounds != null && placeBe.canAddEntry(bounds)) {
                    if (!level.isClientSide) {
                        placeBe.addEntry(data.state(), bounds);
                        if (player == null || !player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        level.sendBlockUpdated(placePos, placeBe.getBlockState(), placeBe.getBlockState(), 3);
                        level.playSound(null, placePos, placeBe.getBlockState().getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            // 重なる場合は通常のBlockItem配置は不可
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!canReplace) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        // 通常の新規ブロック配置（空気またはreplaceable）— ヒット位置でサブ空間を決定
        if (!level.isClientSide) {
            net.minecraft.world.level.block.state.BlockState newState = ruby.bamboo.core.init.BambooBlocks.CUT_BLOCK.get().defaultBlockState()
                    .setValue(ruby.bamboo.block.CutBlock.FACING, facing);
            level.setBlock(placePos, newState, 3);
            if (level.getBlockEntity(placePos) instanceof CutBlockEntity newBe) {
                int[] bounds = CutBlockEntity.computeBoundsFromHit(hitVec, placePos, clickedPos, yLevel, hLevel, facing, clickedFace);
                if (!newBe.addEntry(data.state(), bounds)) {
                    newBe.clearEntries();
                    newBe.addEntry(data.state(), bounds);
                }
                level.sendBlockUpdated(placePos, newState, newState, 3);
            }
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, placePos, newState.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CutBlockItemRenderer.getInstance();
            }
        });
    }

    @Override
    public Component getName(ItemStack stack) {
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir()) {
            return super.getName(stack);
        }
        String baseName = data.state().getBlock().getName().getString();
        int hSize = CutBlockEntity.levelToSize(data.hLevel());
        int ySize = CutBlockEntity.levelToSize(data.yLevel());
        // フルならサイズ省略
        if (hSize == 16 && ySize == 16) {
            return Component.literal(baseName);
        }
        return Component.literal(baseName + " (" + hSize + "×" + ySize + ")");
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // 表示名はgetNameで上書きするので、IDは通常通り
        return super.getDescriptionId(stack);
    }
}
