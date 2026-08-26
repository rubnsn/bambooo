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
 * 表示名を "木材 (8×16×16)" のように3軸で動的に生成。同一サイズはスタック可。
 * 内部形状が levelToSize に収まらない場合は " (カスタム)" 表示。
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
        // 複数Entriesを持つhoe回収品かどうか
        java.util.List<ruby.bamboo.block.entity.CutBlockEntity.CutEntry> multiEntries = ruby.bamboo.block.entity.CutBlockEntity.readEntriesFromStack(stack);
        boolean isMulti = !multiEntries.isEmpty();
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir() && !isMulti) {
            // 空のcut_blockはクラフト以外で生成されるべきではない。置けずにFAIL
            return net.minecraft.world.InteractionResult.FAIL;
        }
        byte xLevel = data.xLevel();
        byte yLevel = data.yLevel();
        byte zLevel = data.zLevel();

        // 1) クリックしたブロックが既存cut_blockなら、その隙間に充填 (単一のみ。複数hoe回収品は充填不可)
        if (!isMulti && level.getBlockEntity(clickedPos) instanceof CutBlockEntity existingBe) {
            if (!existingBe.isEmpty()) {
                int[] bounds = existingBe.findBestBoundsForPlacement(hitVec, clickedPos, xLevel, yLevel, zLevel, clickedFace);
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
        if (!isMulti && level.getBlockEntity(placePos) instanceof CutBlockEntity placeBe) {
            if (!placeBe.isEmpty()) {
                int[] bounds = placeBe.findBestBoundsForPlacement(hitVec, placePos, xLevel, yLevel, zLevel, clickedFace);
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
            return net.minecraft.world.InteractionResult.FAIL;
        }
        // 複数hoe回収品は既存BEへの充填を許可しない。新規空きのみ
        if (isMulti && level.getBlockEntity(placePos) instanceof CutBlockEntity) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!canReplace) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            net.minecraft.world.level.block.state.BlockState newState = ruby.bamboo.core.init.BambooBlocks.CUT_BLOCK.get().defaultBlockState()
                    .setValue(ruby.bamboo.block.CutBlock.FACING, context.getHorizontalDirection().getOpposite());
            // FACINGのY軸対策
            try {
                net.minecraft.core.Direction facing = context.getHorizontalDirection().getOpposite();
                if (facing.getAxis() == net.minecraft.core.Direction.Axis.Y) facing = net.minecraft.core.Direction.NORTH;
                newState = ruby.bamboo.core.init.BambooBlocks.CUT_BLOCK.get().defaultBlockState().setValue(ruby.bamboo.block.CutBlock.FACING, facing);
            } catch (Exception e) {}
            level.setBlock(placePos, newState, 3);
            if (level.getBlockEntity(placePos) instanceof CutBlockEntity newBe) {
                if (isMulti) {
                    net.minecraft.nbt.CompoundTag tag = stack.getTag();
                    if (tag != null && tag.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                        net.minecraft.nbt.CompoundTag bet = tag.getCompound("BlockEntityTag");
                        newBe.readSyncData(bet);
                    } else {
                        // フォールバック: 手動
                        newBe.clearEntries();
                        for (var e : multiEntries) newBe.addEntry(e.state, e.bounds);
                    }
                    level.sendBlockUpdated(placePos, newState, newState, 3);
                } else {
                    int[] bounds = CutBlockEntity.computeBoundsFromHit(hitVec, placePos, clickedPos, xLevel, yLevel, zLevel, null, clickedFace);
                    if (!newBe.addEntry(data.state(), bounds)) {
                        newBe.clearEntries();
                        newBe.addEntry(data.state(), bounds);
                    }
                    level.sendBlockUpdated(placePos, newState, newState, 3);
                }
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
                var r = CutBlockItemRenderer.getInstance();
                if (r != null) return r;
                // サーバ側や初期化失敗時のフォールバックではバニラレンダラーに委譲
                return null;
            }
        });
    }

    @Override
    public Component getName(ItemStack stack) {
        var multi = CutBlockEntity.readEntriesFromStack(stack);
        if (!multi.isEmpty()) {
            // 複数パーツを持つhoe回収品は代表で表示
            try {
                String base = multi.get(0).state.getBlock().getName().getString();
                if (multi.size() > 1) {
                    return Component.literal(base + " (複合×" + multi.size() + ")");
                }
            } catch (Exception e) {}
        }
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir()) {
            // Bounds配列由来のドロップは BlockEntityTag に入っているが CutState が無い場合もある
            // その場合は Bounds からサイズを推定
            try {
                net.minecraft.nbt.CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    net.minecraft.nbt.CompoundTag bet = tag.getCompound("BlockEntityTag");
                    if (bet.contains(CutBlockEntity.TAG_BOUNDS, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                        int[] b = bet.getIntArray(CutBlockEntity.TAG_BOUNDS);
                        if (b.length >= 6) {
                            int xSize = b[3] - b[0];
                            int ySize = b[4] - b[1];
                            int zSize = b[5] - b[2];
                            String base = "カットブロック";
                            // 内部Stateがあればそれを使う
                            if (bet.contains(CutBlockEntity.TAG_STATE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                                try {
                                    net.minecraft.world.level.block.state.BlockState st = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), bet.getCompound(CutBlockEntity.TAG_STATE));
                                    base = st.getBlock().getName().getString();
                                } catch (Exception e) {}
                            }
                            if (xSize == 16 && ySize == 16 && zSize == 16) return Component.literal(base);
                            // サイズが正規(4/8/16)でない場合はカスタム
                            boolean valid = (xSize == 16 || xSize == 8 || xSize == 4) && (ySize == 16 || ySize == 8 || ySize == 4) && (zSize == 16 || zSize == 8 || zSize == 4);
                            if (!valid) return Component.literal(base + " (カスタム)");
                            return Component.literal(base + " (" + xSize + "×" + ySize + "×" + zSize + ")");
                        }
                    }
                }
            } catch (Exception e) {}
            return super.getName(stack);
        }
        String baseName = data.state().getBlock().getName().getString();
        int xSize = CutBlockEntity.levelToSize(data.xLevel());
        int ySize = CutBlockEntity.levelToSize(data.yLevel());
        int zSize = CutBlockEntity.levelToSize(data.zLevel());
        if (xSize == 16 && ySize == 16 && zSize == 16) {
            return Component.literal(baseName);
        }
        // 内部形状不一致チェック: entries由来のBoundsが levelに収まらない場合はカスタムと表示 (通常は起こらないが回収品での不一致対策)
        // ここでは純粋に level 由来なので常に正規
        return Component.literal(baseName + " (" + xSize + "×" + ySize + "×" + zSize + ")");
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return super.getDescriptionId(stack);
    }
}
