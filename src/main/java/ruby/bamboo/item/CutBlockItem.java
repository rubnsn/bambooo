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
 * 3種簡素化: HALF(8x16x16×6姿勢)/EIGHT(8x8x8)/QUARTER(4x4x4)。
 * 表示名は Tier 別、同一Tierはスタック可。
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
        java.util.List<ruby.bamboo.block.entity.CutBlockEntity.CutEntry> multiEntries = ruby.bamboo.block.entity.CutBlockEntity.readEntriesFromStack(stack);
        boolean isMulti = !multiEntries.isEmpty();
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir() && !isMulti) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        CutBlockEntity.Tier tier = CutBlockEntity.Tier.OTHER;
        if (!isMulti) tier = CutBlockEntity.getTierFromLevels(data.xLevel(), data.yLevel(), data.zLevel());
        // 旧27種はOTHERとして扱い置換不可（QAデバッグで互換不要）
        if (!isMulti && tier == CutBlockEntity.Tier.OTHER) return net.minecraft.world.InteractionResult.FAIL;

        // 1) クリックしたブロックが既存cut_blockなら、その隙間に充填
        if (!isMulti && level.getBlockEntity(clickedPos) instanceof CutBlockEntity existingBe) {
            if (!existingBe.isEmpty()) {
                int[] bounds = null;
                if (tier == CutBlockEntity.Tier.HALF) {
                    bounds = computeHalfBoundsForExisting(hitVec, clickedPos, clickedFace);
                } else if (tier == CutBlockEntity.Tier.EIGHT) {
                    bounds = computeCubeBoundsForExisting(hitVec, clickedPos, clickedFace, 8, existingBe);
                } else if (tier == CutBlockEntity.Tier.QUARTER) {
                    bounds = computeCubeBoundsForExisting(hitVec, clickedPos, clickedFace, 4, existingBe);
                }
                if (bounds != null && existingBe.canAddEntry(bounds)) {
                    if (!level.isClientSide) {
                        existingBe.addEntry(data.state(), bounds);
                        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
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
                int[] bounds = null;
                if (tier == CutBlockEntity.Tier.HALF) {
                    bounds = CutBlockEntity.computeHalfBounds(hitVec, clickedPos, clickedFace);
                    // 新規隣接の場合は computeHalfBounds は既に隣接側を返すのでそのまま
                    // ただし existing と new で中心の意味が逆なので、新規の場合はそのまま（隣接側）
                } else if (tier == CutBlockEntity.Tier.EIGHT) {
                    bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, clickedPos, clickedFace, 8);
                } else if (tier == CutBlockEntity.Tier.QUARTER) {
                    bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, clickedPos, clickedFace, 4);
                }
                if (bounds != null && placeBe.canAddEntry(bounds)) {
                    if (!level.isClientSide) {
                        placeBe.addEntry(data.state(), bounds);
                        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
                        level.sendBlockUpdated(placePos, placeBe.getBlockState(), placeBe.getBlockState(), 3);
                        level.playSound(null, placePos, placeBe.getBlockState().getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (isMulti && level.getBlockEntity(placePos) instanceof CutBlockEntity) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!canReplace) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            net.minecraft.world.level.block.state.BlockState newState = ruby.bamboo.core.init.BambooBlocks.CUT_BLOCK.get().defaultBlockState()
                    .setValue(ruby.bamboo.block.CutBlock.FACING, context.getHorizontalDirection().getOpposite());
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
                        newBe.clearEntries();
                        for (var e : multiEntries) newBe.addEntry(e.state, e.bounds);
                    }
                    level.sendBlockUpdated(placePos, newState, newState, 3);
                } else {
                    int[] bounds;
                    if (tier == CutBlockEntity.Tier.HALF) {
                        bounds = CutBlockEntity.computeHalfBounds(hitVec, clickedPos, clickedFace);
                    } else if (tier == CutBlockEntity.Tier.EIGHT) {
                        bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, clickedPos, clickedFace, 8);
                    } else if (tier == CutBlockEntity.Tier.QUARTER) {
                        bounds = CutBlockEntity.computeCubeBoundsForNewPlacement(hitVec, clickedPos, clickedFace, 4);
                    } else {
                        bounds = new int[]{0,0,0,8,16,16};
                    }
                    if (!newBe.addEntry(data.state(), bounds)) {
                        newBe.clearEntries();
                        newBe.addEntry(data.state(), bounds);
                    }
                    level.sendBlockUpdated(placePos, newState, newState, 3);
                }
            }
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, placePos, newState.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int[] computeHalfBoundsForExisting(net.minecraft.world.phys.Vec3 hitVec, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        // 既存ブロック内のヒットは面の反対側（ヒット側）に寄せる。computeHalfBoundsは新規隣接側を返すため反転が必要な場合がある
        // 新規用 computeHalfBounds は隣接側（UP→下、NORTH→南等）を返す。既存の場合はヒット側（UP→上、NORTH→北等）にしたい
        int[] b = CutBlockEntity.computeHalfBounds(hitVec, pos, face);
        // 反転: 新規の隣接側と既存のヒット側は逆なので、Y/Z/Xを反転させる場合がある
        // 簡易: 新規のboundsを180°回転的に反転させるのではなく、hitのfracから直接既存用を計算する
        double fx = hitVec.x - pos.getX();
        double fy = hitVec.y - pos.getY();
        double fz = hitVec.z - pos.getZ();
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        switch (face) {
            case UP -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cz = fz >= 0.25 && fz <= 0.75;
                if (cx && cz) return new int[]{0, 8, 0, 16, 16, 16}; // 上半
                double dx = Math.abs(fx - 0.5), dz = Math.abs(fz - 0.5);
                if (dx > dz) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
            }
            case DOWN -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cz = fz >= 0.25 && fz <= 0.75;
                if (cx && cz) return new int[]{0, 0, 0, 16, 8, 16};
                double dx = Math.abs(fx - 0.5), dz = Math.abs(fz - 0.5);
                if (dx > dz) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
            }
            case NORTH -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cx && cy) return new int[]{0, 0, 0, 16, 16, 8};
                double dx = Math.abs(fx - 0.5), dy = Math.abs(fy - 0.5);
                if (dx > dy) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case SOUTH -> {
                boolean cx = fx >= 0.25 && fx <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cx && cy) return new int[]{0, 0, 8, 16, 16, 16};
                double dx = Math.abs(fx - 0.5), dy = Math.abs(fy - 0.5);
                if (dx > dy) return fx < 0.5 ? new int[]{0,0,0,8,16,16} : new int[]{8,0,0,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case WEST -> {
                boolean cz = fz >= 0.25 && fz <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cz && cy) return new int[]{0, 0, 0, 8, 16, 16};
                double dz = Math.abs(fz - 0.5), dy = Math.abs(fy - 0.5);
                if (dz > dy) return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            case EAST -> {
                boolean cz = fz >= 0.25 && fz <= 0.75;
                boolean cy = fy >= 0.25 && fy <= 0.75;
                if (cz && cy) return new int[]{8, 0, 0, 16, 16, 16};
                double dz = Math.abs(fz - 0.5), dy = Math.abs(fy - 0.5);
                if (dz > dy) return fz < 0.5 ? new int[]{0,0,0,16,16,8} : new int[]{0,0,8,16,16,16};
                else return fy < 0.5 ? new int[]{0,0,0,16,8,16} : new int[]{0,8,0,16,16,16};
            }
            default -> {}
        }
        return b;
    }

    private static int[] computeCubeBoundsForExisting(net.minecraft.world.phys.Vec3 hitVec, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, int size, CutBlockEntity be) {
        // 既存への充填は空きセルからヒット最近傍を選択。findBestBoundsをサイズ固定で呼び出すが、faceロックを回避するためnullで呼ぶ
        byte lvl = CutBlockEntity.sizeToLevel(size);
        int[] best = be.findBestBoundsForPlacement(hitVec, pos, lvl, lvl, lvl, null);
        if (best != null) return best;
        // フォールバック: ヒット位置から直接計算
        double fx = hitVec.x - pos.getX();
        double fy = hitVec.y - pos.getY();
        double fz = hitVec.z - pos.getZ();
        if (fx < 0) fx = 0; if (fx > 1) fx = 1;
        if (fy < 0) fy = 0; if (fy > 1) fy = 1;
        if (fz < 0) fz = 0; if (fz > 1) fz = 1;
        int xo, yo, zo;
        if (size == 8) { xo = fx > 0.5 ? 8 : 0; yo = fy > 0.5 ? 8 : 0; zo = fz > 0.5 ? 8 : 0; }
        else { xo = fx < 0.25 ? 0 : fx < 0.5 ? 4 : fx < 0.75 ? 8 : 12; yo = fy < 0.25 ? 0 : fy < 0.5 ? 4 : fy < 0.75 ? 8 : 12; zo = fz < 0.25 ? 0 : fz < 0.5 ? 4 : fz < 0.75 ? 8 : 12; }
        return new int[]{xo, yo, zo, xo+size, yo+size, zo+size};
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
            try {
                String base = multi.get(0).state.getBlock().getName().getString();
                if (multi.size() > 1) return Component.literal(base + " (複合×" + multi.size() + ")");
            } catch (Exception e) {}
        }
        CutBlockEntity.CutBlockData data = CutBlockEntity.readFromStack(stack);
        if (data.state().isAir()) {
            try {
                net.minecraft.nbt.CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    net.minecraft.nbt.CompoundTag bet = tag.getCompound("BlockEntityTag");
                    if (bet.contains(CutBlockEntity.TAG_BOUNDS, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                        int[] b = bet.getIntArray(CutBlockEntity.TAG_BOUNDS);
                        if (b.length >= 6) {
                            int xSize = b[3] - b[0]; int ySize = b[4] - b[1]; int zSize = b[5] - b[2];
                            String base = "カットブロック";
                            if (bet.contains(CutBlockEntity.TAG_STATE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                                try { net.minecraft.world.level.block.state.BlockState st = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), bet.getCompound(CutBlockEntity.TAG_STATE)); base = st.getBlock().getName().getString(); } catch (Exception e) {}
                            }
                            if (xSize == 16 && ySize == 16 && zSize == 16) return Component.literal(base);
                            String suf = tierSuffix(xSize, ySize, zSize);
                            return Component.literal(base + suf);
                        }
                    }
                }
            } catch (Exception e) {}
            return super.getName(stack);
        }
        CutBlockEntity.Tier t = CutBlockEntity.getTierFromLevels(data.xLevel(), data.yLevel(), data.zLevel());
        String baseName = data.state().getBlock().getName().getString();
        if (t == CutBlockEntity.Tier.HALF) return Component.literal(baseName + " [ハーフ]");
        if (t == CutBlockEntity.Tier.EIGHT) return Component.literal(baseName + " [8x8x8]");
        if (t == CutBlockEntity.Tier.QUARTER) return Component.literal(baseName + " [4x4x4]");
        if (t == CutBlockEntity.Tier.FULL) return Component.literal(baseName);
        int xSize = CutBlockEntity.levelToSize(data.xLevel());
        int ySize = CutBlockEntity.levelToSize(data.yLevel());
        int zSize = CutBlockEntity.levelToSize(data.zLevel());
        if (xSize == 16 && ySize == 16 && zSize == 16) return Component.literal(baseName);
        return Component.literal(baseName + tierSuffix(xSize, ySize, zSize));
    }

    private static String tierSuffix(int x, int y, int z) {
        if ((x==8&&y==16&&z==16)||(x==16&&y==8&&z==16)||(x==16&&y==16&&z==8)) return " [ハーフ]";
        if (x==8&&y==8&&z==8) return " [8x8x8]";
        if (x==4&&y==4&&z==4) return " [4x4x4]";
        return " (" + x + "×" + y + "×" + z + ")";
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return super.getDescriptionId(stack);
    }
}
