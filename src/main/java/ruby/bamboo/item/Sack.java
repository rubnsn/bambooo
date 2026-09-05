package ruby.bamboo.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ruby.bamboo.gui.SackMenu;

/**
 * 袋 (旧 Sack の移植)。
 * <p>
 * BlockItem を最大 {@link #CAPACITY} 個まで1種類だけ収納できるアイテム。
 * <ul>
 * <li>空の袋を右クリック → 1スロット GUI ({@link SackMenu}) を開いて中身を入れる</li>
 * <li>内容ありで空気方向へ右クリック → インベントリから同種 BlockItem を収集</li>
 * <li>ブロックへ右クリック → 内容物として設置 (count 減)</li>
 * <li>種 (BlockItem 派生) の場合は 5x5 範囲へ一括植え (旧仕様踏襲)</li>
 * <li>クラフトに使うと中身が全て排出される ({@link ruby.bamboo.crafting.SackReleaseHandler})</li>
 * </ul>
 * <p>
 * 収容量は NBT の count だけで管理し、耐久システムとは干渉しない
 * ({@code getBarWidth/getBarColor} で残量バー表示)。
 * 旧版の meta / V キー release / renderToolHighlight リフレクションは廃止。
 */
public class Sack extends Item {

    /** 最大収容量 (旧 maxDamage=1025 の内数 1024 相当) */
    public static final int CAPACITY = 1024;

    private static final String TAG_CONTENT = "Content";

    public Sack(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ===== NBT ヘルパー (1.21.1: DataComponents.CUSTOM_DATA 経由) =====

    /** ルートタグのコピーを取得 (CustomData が無ければ empty) */
    private static CompoundTag rootTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** 内容物タグを取得 (無ければ empty。返値はデタッチされたコピーのため読取専用) */
    private static CompoundTag getContentTag(ItemStack stack) {
        CompoundTag root = rootTag(stack);
        if (!root.contains(TAG_CONTENT, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return root.getCompound(TAG_CONTENT);
    }

    /** 内容物があるか */
    public static boolean hasContent(ItemStack sackStack) {
        return !getContentTag(sackStack).isEmpty();
    }

    /** 格納されているアイテムの registry 名 (無ければ null) */
    public static String getContentId(ItemStack sackStack) {
        CompoundTag tag = getContentTag(sackStack);
        return tag.isEmpty() ? null : tag.getString("id");
    }

    /** 収容数 */
    public static int getCount(ItemStack sackStack) {
        CompoundTag tag = getContentTag(sackStack);
        return tag.isEmpty() ? 0 : Math.max(0, tag.getInt("count"));
    }

    /**
     * 内容物 ItemStack を復元する (count はスタック上限でクリップ)。
     * 内容が無ければ empty。
     */
    public static ItemStack getContentStack(ItemStack sackStack, int count) {
        CompoundTag tag = getContentTag(sackStack);
        if (tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = findItem(tag.getString("id"));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int n = Math.min(count, item.getMaxStackSize(new ItemStack(item)));
        return new ItemStack(item, n);
    }

    /** 内容物アイテムを設定 (BlockItem のみ。呼び出し側で isStorage 判定済み前提) */
    public static void setContent(ItemStack sackStack, Item contentItem) {
        CompoundTag root = rootTag(sackStack);
        CompoundTag content = new CompoundTag();
        content.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(contentItem).toString());
        content.putInt("count", 0);
        root.put(TAG_CONTENT, content);
        sackStack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /** 収容数を設定 (0 になったら内容タグごと消す) */
    public static void setCount(ItemStack sackStack, int count) {
        CompoundTag root = rootTag(sackStack);
        if (!root.contains(TAG_CONTENT, Tag.TAG_COMPOUND)) {
            return;
        }
        if (count <= 0) {
            root.remove(TAG_CONTENT);
            if (root.isEmpty()) {
                sackStack.remove(DataComponents.CUSTOM_DATA);
            } else {
                sackStack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            }
        } else {
            CompoundTag content = root.getCompound(TAG_CONTENT).copy();
            content.putInt("count", Math.min(count, CAPACITY));
            root.put(TAG_CONTENT, content);
            sackStack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    private static Item findItem(String id) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        return key == null ? null : registry.get(key);
    }

    /** 収容可能か (BlockItem のみ: sakura Fukuro 準拠) */
    public static boolean isStorage(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem;
    }

    // ===== 外観 =====

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasContent(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = super.getName(stack);
        if (!hasContent(stack)) {
            return base;
        }
        Item item = findItem(getContentId(stack));
        Component itemName = item == null ? Component.literal("?") : item.getName(new ItemStack(item));
        return base.copy().append(":").append(itemName)
                .append(":" + getCount(stack));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (hasContent(stack)) {
            tooltip.add(Component.translatable("tooltip.bamboomod.sack.count", getCount(stack), CAPACITY)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 残量バー (getBarWidth は 13px 基準で返す) */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return hasContent(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return hasContent(stack) ? Math.round(13.0F * getCount(stack) / CAPACITY) : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFFFFFFFF; // 白固定
    }

    // ===== 操作 =====

    /**
     * 空の袋 → GUI オープン。内容あり → インベントリ収集。
     * (旧 onItemRightClick 相当。「内容物を使う」挙動は移植時に省略した)
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!hasContent(stack)) {
            // 空袋: 収容用 GUI を開く
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new SackMenu(id, inv),
                        this.getName(stack)));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // 収集モード: 手持ちインベントリから同種 BlockItem を合算
        if (!level.isClientSide) {
            collect(level, player, stack);
        } else {
            level.playSound(player, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 0.4F, 1.2F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** インベントリから同種アイテムを収集して count に加算 */
    private static void collect(Level level, Player player, ItemStack sackStack) {
        String id = getContentId(sackStack);
        Item target = findItem(id);
        if (target == null) {
            return;
        }
        int stored = getCount(sackStack);

        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty() || slot.getItem() != target) {
                continue;
            }
            int take = Math.min(slot.getCount(), CAPACITY - stored);
            if (take <= 0) {
                break;
            }
            stored += take;
            slot.shrink(take);
        }

        setCount(sackStack, stored);
    }

    /**
     * ブロックへの設置 (旧 onItemUse 相当)。
     * 内容物 BlockItem の useOn に委譲し、成功時 count--。
     * 種など「植える」BlockItem は 5x5 範囲へ一括試行 (旧仕様踏襲)。
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack sackStack = context.getItemInHand();
        if (!hasContent(sackStack)) {
            return InteractionResult.PASS;
        }
        Item content = findItem(getContentId(sackStack));
        if (!(content instanceof net.minecraft.world.item.BlockItem blockItem)) {
            return InteractionResult.PASS;
        }

        var pos = context.getClickedPos();

        if (isPlantable(blockItem)) {
            return plantArea(context, blockItem, pos);
        }

        // 通常設置: 内容物 1 個分のスタックを作って委譲
        ItemStack one = getContentStack(sackStack, 1);
        if (one.isEmpty()) {
            return InteractionResult.PASS;
        }
        net.minecraft.world.item.context.UseOnContext subContext = new net.minecraft.world.item.context.UseOnContext(
                level, player, context.getHand(), one,
                new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                        context.getClickedFace(), pos, context.isInside()));
        InteractionResult result = blockItem.useOn(subContext);
        if (result.consumesAction()) {
            decrement(level, player, sackStack, pos);
            return InteractionResult.SUCCESS;
        }
        return result;
    }

    /** 種系 (CropBlock 派生 = 耕地に植えるもの) を一括植え対象とみなす */
    private static boolean isPlantable(net.minecraft.world.item.BlockItem blockItem) {
        net.minecraft.world.level.block.Block block = blockItem.getBlock();
        return block instanceof net.minecraft.world.level.block.CropBlock;
    }

    /** 5x5 一括植え (旧 onItemUse の種処理相当) */
    private static InteractionResult plantArea(net.minecraft.world.item.context.UseOnContext context,
            net.minecraft.world.item.BlockItem blockItem, net.minecraft.core.BlockPos center) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack sackStack = context.getItemInHand();
        int stored = getCount(sackStack);
        if (stored <= 0) {
            return InteractionResult.PASS;
        }

        int planted = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (stored - planted <= 0) {
                    // 在庫切れ
                    return InteractionResult.SUCCESS;
                }
                var pos = center.offset(dx, 0, dz);
                ItemStack one = getContentStack(sackStack, 1);
                if (one.isEmpty()) {
                    break;
                }
                var hit = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                        context.getClickedFace(), pos, context.isInside());
                net.minecraft.world.item.context.UseOnContext subContext = new net.minecraft.world.item.context.UseOnContext(
                        level, player, context.getHand(), one, hit);
                InteractionResult r = blockItem.useOn(subContext);
                if (r.consumesAction()) {
                    planted++;
                }
            }
        }

        if (planted > 0) {
            setCount(sackStack, stored - planted);
            level.playSound(null, center, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 設置成功後の共通後始末: count-- + 効果音 */
    private static void decrement(Level level, Player player, ItemStack sackStack,
            net.minecraft.core.BlockPos pos) {
        int stored = getCount(sackStack);
        setCount(sackStack, stored - 1);
        level.playSound(null, pos, blockPlaceSound(stored), SoundSource.BLOCKS, 0.8F, 1.0F);
        if (stored - 1 <= 0) {
            // 使い切った: 音だけ変えて終了 (NBT は setCount(0) で消滅済み)
        }
    }

    private static net.minecraft.sounds.SoundEvent blockPlaceSound(int stored) {
        return stored > 1 ? SoundEvents.STONE_PLACE : SoundEvents.ITEM_BREAK;
    }

    // ===== クラフト用ヘルパー =====

    /**
     * クラフトグリッド上の袋から中身を全て吐き出し、空の袋へ戻す。
     * {@link ruby.bamboo.crafting.SackReleaseHandler} から呼ばれる。
     *
     * @return 吐き出した ItemStack のリスト (空なら何もしなかった)
     */
    public static List<ItemStack> releaseAll(Level level, Player player, ItemStack sackStack) {
        if (!hasContent(sackStack)) {
            return List.of();
        }
        Item content = findItem(getContentId(sackStack));
        int count = getCount(sackStack);
        setCount(sackStack, 0); // NBT クリア
        if (content == null || count <= 0) {
            return List.of();
        }

        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<>();
        while (count > 0) {
            int n = Math.min(count, content.getMaxStackSize(new ItemStack(content)));
            drops.add(new ItemStack(content, n));
            count -= n;
        }
        return drops;
    }
}


    // ===== 操作 =====
