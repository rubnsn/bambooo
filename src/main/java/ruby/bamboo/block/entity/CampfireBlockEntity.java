package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.CampfireBlock;
import ruby.bamboo.core.init.BambooBlockEntities;
import ruby.bamboo.crafting.cooking.BambooCampfireRecipe;
import ruby.bamboo.gui.CampfireMenu;

/**
 * 囲炉裏の BlockEntity (旧 TileCampfire の移植)。
 * <p>
 * スロット構成 (旧仕様と同一):
 * <ul>
 * <li>0-8: 3×3クラフトマトリクス</li>
 * <li>9: 燃料</li>
 * <li>10: 結果 (取り出し専用)</li>
 * </ul>
 * <p>
 * 旧仕様の踏襲点:
 * <ul>
 * <li>MAX_FUEL=102400 (燃料貯蔵上限)</li>
 * <li>燃料はバニラ精錬の燃料表 (ForgeHooks.getBurnTime)</li>
 * <li>調理中は copyMatrix の値比較で素材変更を検知して中止</li>
 * <li>BakeType (NONE/ATHER/MEAT/FISH) を結果スロットから判定し BER 描画に使用</li>
 * </ul>
 */
public class CampfireBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {

    /** 焼き種別 (旧 TileCampfire.BakeType) */
    public enum BakeType {
        NONE, ATHER, MEAT, FISH
    }

    private static final int SLOT_FUEL = 9;
    private static final int SLOT_RESULT = 10;
    private static final int[] SLOTS_TOP = new int[] { 0 };
    private static final int[] SLOTS_BOTTOM = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, SLOT_FUEL, SLOT_RESULT };
    private static final int[] SLOTS_SIDES = new int[] { SLOT_FUEL };

    /** 燃料貯蔵上限 (旧 MAX_FUEL) */
    public static final int MAX_FUEL = 102400;

    /** スロット: 0-8=素材 / 9=燃料 / 10=結果 */
    private NonNullList<ItemStack> items = NonNullList.withSize(11, ItemStack.EMPTY);
    /** 調理開始時のマトリクススナップショット (途中変更検知用) */
    private ItemStack[] copyMatrix = new ItemStack[9];

    private int fuel;
    private int maxCookTime = 200;
    private int cookTime = 200;
    private boolean isBurn = false;
    private BambooCampfireRecipe entry;
    private ItemStack nowCookingResult = ItemStack.EMPTY;

    /** クライアント側 (GUI用) */
    public int fuelRatio;
    public int cookRatio = 100;

    /** 回転肉の角度 (BER用) */
    private int meatroll;
    private BakeType nowBakeType = BakeType.NONE;

    private final LazyOptional<IItemHandlerModifiable>[] itemHandlers = SidedInvWrapper.create(this, Direction.values());

    public CampfireBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.CAMPFIRE_BE.get(), pos, state);
        for (int i = 0; i < 9; i++) {
            copyMatrix[i] = ItemStack.EMPTY;
        }
    }

    // ===== 毎tick処理 =====

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof CampfireBlockEntity campfire) {
            if (!level.isClientSide) {
                campfire.serverTick();
            } else {
                campfire.clientTick();
            }
        }
    }

    private void serverTick() {
        updateFuel();
        updateCooking();
        updateRender();
    }

    private void clientTick() {
        // 肉の回転アニメ (旧 updateRender の meatroll 相当)
        meatroll = meatroll < 360 ? ++meatroll : 0;
    }

    /** 燃料投入 (旧 updateFuel 相当) */
    private void updateFuel() {
        ItemStack fuelStack = items.get(SLOT_FUEL);
        if (!fuelStack.isEmpty()) {
            int burnTime = net.minecraftforge.common.ForgeHooks.getBurnTime(fuelStack, null);
            if (burnTime > 0 && fuel + burnTime <= MAX_FUEL) {
                fuel += burnTime;
                fuelStack.shrink(1);
                if (fuelStack.isEmpty()) {
                    // 容器返却 (バケツ等)
                    Item remaining = fuelStack.getItem().getCraftingRemainingItem();
                    items.set(SLOT_FUEL, remaining == null ? ItemStack.EMPTY : new ItemStack(remaining));
                }
            }
        }
    }

    /** 調理開始判定 (旧 updateCooking 相当) */
    private void updateCooking() {
        if (!isBurn && !isMatrixEmpty()) {
            ItemStack result = items.get(SLOT_RESULT);
            if (!result.isEmpty() && result.getCount() == result.getMaxStackSize()) {
                return;
            }
            if (canCooking() && entry.fuelCost() <= fuel) {
                if (nowCookingResult.isEmpty()) {
                    startCooking();
                } else if (result.isEmpty()) {
                    startCooking();
                } else if (ItemStack.isSameItemSameTags(nowCookingResult, result)
                        && result.getCount() + nowCookingResult.getCount() <= result.getMaxStackSize()) {
                    startCooking();
                }
            }
        } else {
            updateBurn();
        }
    }

    private void startCooking() {
        isBurn = true;
        maxCookTime = cookTime = entry.cookingTime();
        setMatrix();
    }

    /** 調理進行 (旧 updateBurn 相当) */
    private void updateBurn() {
        if (isBurn) {
            if (--cookTime <= 0) {
                // 完成: レシピ再検索して結果と一致すれば材料消費+結果追加
                BambooCampfireRecipe nowEntry = findRecipe();
                if (nowEntry != null && ItemStack.isSameItemSameTags(nowEntry.getResultItem(level.registryAccess()), nowCookingResult)) {
                    ItemStack result = items.get(SLOT_RESULT);
                    if (result.isEmpty()) {
                        materialConsumption(nowEntry);
                        items.set(SLOT_RESULT, nowCookingResult.copy());
                    } else if (ItemStack.isSameItemSameTags(nowCookingResult, result)
                            && result.getCount() + nowCookingResult.getCount() <= result.getMaxStackSize()) {
                        materialConsumption(nowEntry);
                        result.grow(nowCookingResult.getCount());
                    }
                    setChanged();
                }
                nowCookingResult = ItemStack.EMPTY;
                isBurn = false;
                maxCookTime = cookTime = 200;
            }
            // 偶数tickごとに素材変更を検知して中止
            if ((cookTime & 1) == 0) {
                if (!chkMatrix()) {
                    nowCookingResult = ItemStack.EMPTY;
                    isBurn = false;
                    maxCookTime = cookTime = 200;
                }
            }
            // 100tickごとに同期
            if (cookTime % 100 == 0) {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 7);
            }
        }
    }

    /** BakeType 判定 (旧 updateRender 相当) */
    private void updateRender() {
        BakeType type = BakeType.NONE;
        ItemStack result = items.get(SLOT_RESULT);
        if (!result.isEmpty()) {
            if (result.is(Items.COOKED_COD) || result.is(Items.COOKED_SALMON)) {
                type = BakeType.FISH;
            } else if (result.is(Items.COOKED_PORKCHOP) || result.is(Items.COOKED_BEEF)) {
                type = BakeType.MEAT;
            } else {
                type = BakeType.ATHER;
            }
        }
        if (nowBakeType != type) {
            nowBakeType = type;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ===== 調理ロジック =====

    private boolean isMatrixEmpty() {
        for (int i = 0; i < 9; i++) {
            if (!items.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean canCooking() {
        BambooCampfireRecipe found = findRecipe();
        if (found != null) {
            this.entry = found;
            this.nowCookingResult = found.getResultItem(level.registryAccess()).copy();
            return true;
        }
        return false;
    }

    private BambooCampfireRecipe findRecipe() {
        if (level == null) return null;
        // 0-8 をSimpleContainerで検索 (9サイズ。 fuel/result は含めない)
        net.minecraft.world.SimpleContainer inv = new net.minecraft.world.SimpleContainer(9);
        for (int i = 0; i < 9; i++) inv.setItem(i, items.get(i).copy());
        var campfire = level.getRecipeManager().getRecipeFor(BambooMod.CAMPFIRE_RECIPE_TYPE.get(), inv, level);
        if (campfire.isPresent()) {
            return campfire.orElse(null);
        }
        // バニラ精錬フォールバック (素材1個時のみ) — canCooking/updateBurn 共通。cookingTime/experienceはバニラ準拠、fuelCostは囲炉裏固定200
        if (countNonEmpty() == 1) {
            ItemStack single = getSingleStack();
            if (single != null) {
                var smelting = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                        new net.minecraft.world.SimpleContainer(single), level);
                if (smelting.isPresent()) {
                    var recipe = smelting.get();
                    ItemStack res = recipe.getResultItem(level.registryAccess());
                    if (!res.isEmpty()) {
                        NonNullList<net.minecraft.world.item.crafting.Ingredient> ing = NonNullList.create();
                        ing.add(net.minecraft.world.item.crafting.Ingredient.of(single));
                        return new BambooCampfireRecipe(
                                new net.minecraft.resources.ResourceLocation("bamboomod", "smelting_" + recipe.getId().getPath()),
                                "", BambooCampfireRecipe.Category.MISC, ing, res.copy(), recipe.getExperience(), recipe.getCookingTime(), 200);
                    }
                }
            }
        }
        return null;
    }

    private int countNonEmpty() {
        int c = 0;
        for (int i = 0; i < 9; i++) if (!items.get(i).isEmpty()) c++;
        return c;
    }

    private ItemStack getSingleStack() {
        for (int i = 0; i < 9; i++) if (!items.get(i).isEmpty()) return items.get(i);
        return null;
    }

    private void setMatrix() {
        for (int i = 0; i < 9; i++) {
            copyMatrix[i] = items.get(i).copy();
        }
    }

    /** 素材変更検知 (旧 chkMtrix 相当。値比較に修正) */
    private boolean chkMatrix() {
        for (int i = 0; i < 9; i++) {
            if (!ItemStack.isSameItemSameTags(copyMatrix[i], items.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** 材料消費 (旧 materialConsumption 相当) */
    private void materialConsumption(BambooCampfireRecipe recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty()) {
                slot.shrink(1);
                if (slot.isEmpty()) {
                    Item remaining = slot.getItem().getCraftingRemainingItem();
                    items.set(i, remaining == null ? ItemStack.EMPTY : new ItemStack(remaining));
                }
            }
        }
        fuel -= recipe.fuelCost();
    }

    /** レシピブック用: StackedContents への充填 */
    public void fillStackedContents(net.minecraft.world.entity.player.StackedContents helper) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) helper.accountStack(s);
        }
    }

    private ItemStack[] toMatrixArray() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = items.get(i);
        }
        return matrix;
    }

    // ===== GUI連携値 =====

    public BakeType getBakeType() {
        return nowBakeType;
    }

    public int getMeatroll() {
        return meatroll;
    }

    /** BER回転角度 (旧 getRotate 相当) */
    public float getRotate() {
        Direction facing = getBlockState().getValue(CampfireBlock.FACING);
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 270;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    public int getFuelAmount() {
        return getRatio(fuel, MAX_FUEL, 100);
    }

    public int getCookAmount() {
        return getRatio(cookTime, maxCookTime, 100);
    }

    private int getRatio(float a, float b, int scale) {
        return Math.round(a / b * scale);
    }

    // ===== WorldlyContainer (ホッパー連携) =====

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_BOTTOM : (side == Direction.UP ? SLOTS_TOP : SLOTS_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 旧 isItemValidForSlot: slot2(結果)は不可 / slot9(燃料)は燃料品のみ / 他は可
        if (index == SLOT_RESULT) {
            return false;
        }
        if (index == SLOT_FUEL) {
            return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
        }
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        // 旧 canExtractItem: バケツ or 結果スロットのみ
        return stack.is(Items.BUCKET) || index == SLOT_RESULT;
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

    /** GUIタイトル (旧 getName "bamboo.container.campfire" 相当) */
    public Component getDefaultName() {
        return Component.translatable("container.bamboomod.campfire");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CampfireMenu(containerId, playerInventory, this,
                new net.minecraft.world.inventory.SimpleContainerData(2) {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> CampfireBlockEntity.this.getFuelAmount();
                    case 1 -> CampfireBlockEntity.this.getCookAmount();
                    default -> 0;
                };
            }
        });
    }

    @Override
    public Component getDisplayName() {
        return getDefaultName();
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("fuel", fuel);
        tag.putInt("cookTime", cookTime);
        tag.putInt("maxCookTime", maxCookTime);
        if (!nowCookingResult.isEmpty()) {
            tag.put("nowItem", nowCookingResult.save(new CompoundTag()));
        }
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(11, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains("fuel")) {
            this.fuel = tag.getInt("fuel");
        }
        if (tag.contains("cookTime")) {
            this.cookTime = tag.getInt("cookTime");
        }
        if (tag.contains("maxCookTime")) {
            this.maxCookTime = tag.getInt("maxCookTime");
        }
        if (tag.contains("nowItem")) {
            this.nowCookingResult = ItemStack.of(tag.getCompound("nowItem"));
        }
    }

    // ===== クライアント同期 (BakeType 用) =====

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeSyncData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readSyncData(tag);
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        writeSyncData(tag);
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        readSyncData(pkt.getTag());
    }

    private void writeSyncData(CompoundTag tag) {
        tag.putString("bakeType", nowBakeType.name());
        tag.putInt("time", cookTime);
        tag.putInt("maxtime", maxCookTime);
    }

    private void readSyncData(CompoundTag tag) {
        if (tag.contains("bakeType")) {
            try {
                nowBakeType = BakeType.valueOf(tag.getString("bakeType"));
            } catch (IllegalArgumentException e) {
                nowBakeType = BakeType.NONE;
            }
        }
        if (tag.contains("time")) {
            cookTime = tag.getInt("time");
        }
        if (tag.contains("maxtime")) {
            maxCookTime = tag.getInt("maxtime");
        }
    }

    // ===== Capability (ホッパー/パイプ連携) =====

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null) {
            return itemHandlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<IItemHandlerModifiable> handler : itemHandlers) {
            handler.invalidate();
        }
    }

    /** 破壊時に中身を散布 (CampfireBlock.onRemove から呼ばれる) */
    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }
}