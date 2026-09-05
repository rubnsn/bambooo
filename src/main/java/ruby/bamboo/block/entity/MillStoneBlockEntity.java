package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.MillStoneBlock;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.crafting.grind.BambooGrindRecipe;
import ruby.bamboo.gui.MillStoneMenu;

/**
 * 石臼の BlockEntity (旧 TileMillStone の移植)。
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>grindTime は 0→400 (MAX_GRINDTIME) への<b>カウントアップ式</b>。400超で完成</li>
 * <li>入力消費量はレシピの必要個数 (稲の種なら4個/回)</li>
 * <li>完成時のレシピ再検索は「開始時に記録したアイテム」で行う (途中入替えの影響を受けない)</li>
 * <li>grindMotion (0-3) をブロックステート GRIND_MOTION に同期し、回転描画・GUIアニメに使用</li>
 * <li>クライアントでは roll 角を tick 毎に加算して滑らかに回転 + ITEM_CRACK パーティクル</li>
 * </ul>
 */
public class MillStoneBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {

    /** 旧 MAX_GRINDTIME = 400tick (=20秒) */
    public static final int MAX_GRINDTIME = 400;
    /** GUIプログレスバー段数 (旧 MAX_PROGRESS) */
    public static final int MAX_PROGRESS = 3;

    private static final int[] SLOTS_TOP = new int[] { 0 };
    private static final int[] SLOTS_BOTTOM = new int[] { 2, 1 };
    private static final int[] SLOTS_SIDES = new int[] { 0 };

    /** blockEvent id: 粉砕中アイテムの同期 (param = Item registry id) */
    public static final int EVENT_SYNC_ITEM = 1;

    /** スロット: 0=入力 / 1=メイン出力 / 2=ボーナス出力 */
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);

    /** 粉砕進行度 (0..400)。0=待機 */
    private int grindTime;
    /** 粉砕モーション (0-3)。grindTime%40/10。ブロックステート同期・GUIアニメ用 */
    private int grindMotion;
    /** 現在粉砕中のアイテム (パーティクル表示用)。待機時は AIR。サーバー→クライアント同期 */
    private Item grindingItem = Items.AIR;

    /** クライアント専用: 石車の連続回転角 (度)。旧 roll 相当 */
    public float roll;
    /** クライアント専用: 前tickの回転角 (partialTicks補間用) */
    public float prevRoll;

    private static final RandomSource random = RandomSource.create();

    // ホッパー連携は NeoForge BlockCapability へ移行 (BambooCapabilities.registerCaps で登録)。

    public MillStoneBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.MILL_STONE_BE.get(), pos, state);
    }

    // ===== 毎tick処理 =====

    /**
     * Block#getTicker から登録されるティッカー。
     * サーバー: 粉砕ロジック / クライアント: 回転角更新+パーティクル。
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof MillStoneBlockEntity mill) {
            if (level.isClientSide) {
                mill.clientTick(state);
            } else {
                mill.serverTick();
            }
        }
    }

    private BambooGrindRecipe currentRecipe;

    private void serverTick() {
        boolean dirty = false;

        if (grindTime == 0) {
            BambooGrindRecipe recipe = findRecipe(items.get(0));
            if (recipe != null && canStoreResult(recipe)) {
                // 粉砕開始: レシピを保持して必要数だけ消費
                this.currentRecipe = recipe;
                consumeInput(recipe);
                grindTime += 1;
                syncBlockState(grindTime % 40 / 10);
                dirty = true;
            } else {
                this.currentRecipe = null;
                syncBlockState(0);
            }
        } else {
            grindTime += 1;
            if (grindTime > MAX_GRINDTIME) {
                grindTime = 0;
                grindItem();
            }
            syncBlockState(grindTime % 40 / 10);
            dirty = true;
        }

        if (dirty) {
            setChanged();
        }
    }

    private BambooGrindRecipe findRecipe(ItemStack stack) {
        if (stack.isEmpty() || level == null) return null;
        return level.getRecipeManager()
                .getRecipeFor(BambooMod.MILLSTONE_RECIPE_TYPE.get(),
                        new net.minecraft.world.item.crafting.SingleRecipeInput(stack.copy()), level)
                .map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
    }

    /** RecipeManager用: StackedContentsへの充填 (レシピブックのフィルタ判定) */
    public void fillStackedContents(net.minecraft.world.entity.player.StackedContents helper) {
        ItemStack s = items.get(0);
        if (!s.isEmpty()) helper.accountStack(s);
    }

    private void clientTick(BlockState state) {
        // GRINDING フラグで稼働判定するため、grindMotion が 0 に循環しても回転はリセットされない
        if (state.getValue(MillStoneBlock.GRINDING)) {
            int motion = state.getValue(MillStoneBlock.GRIND_MOTION);
            prevRoll = roll;
            roll += Math.max(motion, 1);
            spawnCrackParticles();
        } else {
            roll = 0.0F;
            prevRoll = 0.0F;
        }
    }

    /** 粉砕中アイテムの破片パーティクル (旧 itemCrackParticle の簡略再現) */
    private void spawnCrackParticles() {
        if (grindingItem == Items.AIR || level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(grindingItem)),
                x, y, z,
                (random.nextDouble() - 0.5) * 0.1,
                random.nextDouble() * 0.1 + 0.05,
                (random.nextDouble() - 0.5) * 0.1);
    }

    /**
     * grindMotion と grinding をブロックステートへ同期 (旧 updateMeta 相当)。
     * <p>
     * 変化があった場合のみ setBlock する。GRINDING は「粉砕中か」を示し、
     * クライアントはこれで回転の継続/停止を判定する (旧 META==0 判定の代替)。
     */
    private void syncBlockState(int motion) {
        this.grindMotion = motion;
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof MillStoneBlock) {
                boolean grinding = grindTime > 0;
                if (state.getValue(MillStoneBlock.GRIND_MOTION) != motion
                        || state.getValue(MillStoneBlock.GRINDING) != grinding) {
                    level.setBlock(worldPosition,
                            state.setValue(MillStoneBlock.GRIND_MOTION, motion)
                                    .setValue(MillStoneBlock.GRINDING, grinding),
                            3);
                }
            }
        }
    }

    // ===== 粉砕ロジック =====

    /**
     * 完成結果を出力スロットへ格納できるか (旧 canGrind の出力側チェック)。
     * 出力スロットの空き・同種マージ・スタック上限まで確認する。
     */
    private boolean canStoreResult(BambooGrindRecipe recipe) {
        ItemStack output = recipe.getResultItem(level != null ? level.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY);
        // BambooGrindRecipeはresultを直接保持
        ItemStack slot1 = items.get(1);
        ItemStack slot2 = items.get(2);

        // 出力先両方空なら OK
        if (slot1.isEmpty() && slot2.isEmpty()) {
            return true;
        }
        // ボーナス欄が埋まっているのにレシピにボーナスが無い → NG
        if (!slot2.isEmpty() && !recipe.hasBonus()) {
            return false;
        }
        // 既存と異種 → NG
        if (!slot1.isEmpty() && !ItemStack.isSameItemSameComponents(slot1, output)) {
            return false;
        }
        if (recipe.hasBonus() && !slot2.isEmpty() && !ItemStack.isSameItemSameComponents(slot2, recipe.bonus())) {
            return false;
        }
        // スタック上限チェック (output)
        int outResult = slot1.isEmpty() ? output.getCount() : slot1.getCount() + output.getCount();
        boolean ok = outResult <= Math.min(getMaxStackSize(), output.getMaxStackSize());
        // スタック上限チェック (bonus) - ボーナスは確率だが上限は満たす必要あり
        if (ok && recipe.hasBonus()) {
            ItemStack bonus = recipe.bonus();
            int bonusResult = slot2.isEmpty() ? bonus.getCount() : slot2.getCount() + bonus.getCount();
            ok = bonusResult <= Math.min(getMaxStackSize(), bonus.getMaxStackSize());
        }
        return ok;
    }

    /** 粉砕開始: 入力アイテムを記録し、レシピ必要数だけ消費 (旧 decrementSlot0 相当) */
    private void consumeInput(BambooGrindRecipe recipe) {
        this.grindingItem = items.get(0).getItem();
        items.get(0).shrink(recipe.inputCount());
        if (items.get(0).isEmpty()) {
            items.set(0, ItemStack.EMPTY);
        }
        // 粉砕中アイテムをクライアントへ通知 (blockEvent → triggerEvent)。
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_SYNC_ITEM,
                    Item.getId(this.grindingItem));
        }
    }

    /** 粉砕完成: 保持したレシピで出力を格納 (旧 grindItem 相当。ランダムボーナスは保持) */
    private void grindItem() {
        if (currentRecipe != null) {
            ItemStack output = currentRecipe.getResultItem(level.registryAccess());
            mergeIntoSlot(1, output);
            if (currentRecipe.hasBonus() && random.nextFloat() <= currentRecipe.bonusChance()) {
                mergeIntoSlot(2, currentRecipe.bonus());
            }
            currentRecipe = null;
            this.grindingItem = Items.AIR;
        } else if (grindingItem != Items.AIR) {
            // フォールバック: 記録アイテムから再検索 (旧仕様互換, 通常は到達しない)
            BambooGrindRecipe r = level != null ? findRecipe(new ItemStack(grindingItem)) : null;
            if (r != null) {
                mergeIntoSlot(1, r.getResultItem(level.registryAccess()));
                if (r.hasBonus() && random.nextFloat() <= r.bonusChance()) mergeIntoSlot(2, r.bonus());
            }
            this.grindingItem = Items.AIR;
        }
    }

    /** 出力スロットへのマージ追加 (同種なら加算) */
    private void mergeIntoSlot(int index, ItemStack stack) {
        ItemStack current = items.get(index);
        if (current.isEmpty()) {
            items.set(index, stack.copy());
        } else if (ItemStack.isSameItemSameComponents(current, stack)) {
            current.grow(stack.getCount());
        }
    }

    // ===== GUI連携値 =====

    public int getGrindTime() {
        return grindTime;
    }

    public int getGrindMotion() {
        return grindMotion;
    }

    public boolean isGrinding() {
        return grindTime > 0;
    }

    /** GUIプログレスバー段数 (0-3)。旧 getProgress 相当 */
    public int getProgress() {
        return Math.round((float) grindTime / MAX_GRINDTIME * MAX_PROGRESS);
    }

    // ===== WorldlyContainer (ホッパー連携) =====

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_BOTTOM : (side == Direction.UP ? SLOTS_TOP : SLOTS_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 挿入は入力スロットのみ
        return index == 0;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 下から入力スロットを引き抜くことだけ禁止 (旧 canExtractItem 相当)
        return !(direction == Direction.DOWN && index == 0);
    }

    // ===== Container 基本実装 =====

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack taken = net.minecraft.world.ContainerHelper.removeItem(items, index, count);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return net.minecraft.world.ContainerHelper.takeItem(items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** GUIタイトル (旧 getName "tile.MillStone" 相当) */
    public Component getDefaultName() {
        return Component.translatable("container.bamboomod.mill_stone");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MillStoneMenu(containerId, playerInventory, this,
                new net.minecraft.world.inventory.SimpleContainerData(3) {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> MillStoneBlockEntity.this.grindMotion;
                    case 1 -> MillStoneBlockEntity.this.getProgress();
                    case 2 -> MillStoneBlockEntity.this.isGrinding() ? 1 : 0;
                    default -> 0;
                };
            }
        });
    }

    @Override
    public Component getDisplayName() {
        return getDefaultName();
    }

    /**
     * blockEvent 受信 (サーバー/クライアント両方で呼ばれる)。
     * クライアント側で粉砕中アイテムを受け取り、パーティクル表示に使用する。
     */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_SYNC_ITEM) {
            this.grindingItem = param > 0 ? Item.byId(param) : Items.AIR;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("grindTime", grindTime);
        tag.putString("grindItemName",
                BuiltInRegistries.ITEM.getKey(grindingItem == null ? Items.AIR : grindingItem).toString());
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(3, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items, registries);
        if (tag.contains("grindTime")) {
            this.grindTime = tag.getInt("grindTime");
        }
        if (tag.contains("grindItemName")) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("grindItemName")));
            this.grindingItem = item == null ? Items.AIR : item;
        }
    }

    // ===== クライアント同期 (粉砕中アイテムのパーティクル用) =====

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSyncData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readSyncData(tag);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        writeSyncData(tag);
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this, (be, registries) -> tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        readSyncData(pkt.getTag());
    }

    private void writeSyncData(CompoundTag tag) {
        tag.putString("grindItemName",
                BuiltInRegistries.ITEM.getKey(grindingItem == null ? Items.AIR : grindingItem).toString());
    }

    private void readSyncData(CompoundTag tag) {
        if (tag.contains("grindItemName")) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("grindItemName")));
            this.grindingItem = item == null ? Items.AIR : item;
        }
    }

    // ===== Capability (ホッパー/パイプ連携: NeoForge BlockCapability へ移行) =====
    // 旧 getCapability/invalidateCaps (ForgeCapabilities/LazyOptional) は削除。
    // 登録は BambooCapabilities.registerCaps (RegisterCapabilitiesEvent) で行う。

    /** 破壊時に中身を散布 (JPChestBlock.onRemove から呼ばれる) */
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }
}