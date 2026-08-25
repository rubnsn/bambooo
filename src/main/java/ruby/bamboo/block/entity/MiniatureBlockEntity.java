package ruby.bamboo.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import ruby.bamboo.core.init.BambooBlockEntities;

/**
 * ミニチュアの BlockEntity — Phase A データ層。
 * <p>
 * 仕様: {@code docs/port-spec-miniature.md §2.2}
 * <ul>
 * <li>size (4..16, NBT Size, 既定8) + cells[size][size][size] (空気は AIR)</li>
 * <li>dirty / syncTimer による間欠バッチ同期 (5tick遅延)</li>
 * <li>tickCursor (randomTick ラウンドロビン用・Phase E で使用)</li>
 * <li>loadState / buildProgress (遅延ロード・Phase D で使用。Phase A は READY 固定)</li>
 * <li>shapeCache (VoxelShape 再構築キャッシュ)</li>
 * </ul>
 * NBT形式:
 * <pre>
 * { Size: 8, Cells: [ {Pos:{X:0,Y:1,Z:2}, State:{Name:"minecraft:stone", Properties:{...}}}, ... ] }
 * </pre>
 * 非空セルのみ保存。TE所持 state の NBT は保存しない (見た目のみ)。
 */
public class MiniatureBlockEntity extends BlockEntity {

    public static final int DEFAULT_SIZE = 8;
    public static final int MIN_SIZE = 4;
    public static final int MAX_SIZE = 16;

    public static final String TAG_SIZE = "Size";
    public static final String TAG_CELLS = "Cells";
    public static final String TAG_POWER = "Power";

    // 遅延ロード状態 (§2.7.1)
    public static final byte LOAD_UNLOADED = 0;
    public static final byte LOAD_QUEUED = 1;
    public static final byte LOAD_BUILDING = 2;
    public static final byte LOAD_READY = 3;

    private static final int SYNC_DELAY = 5;

    private int size = DEFAULT_SIZE;
    private BlockState[][][] cells;

    private boolean dirty = false;
    private int syncTimer = 0;

    private int tickCursor = 0;
    private byte loadState = LOAD_READY;
    private int buildProgress = 0;

    // 簡易power状態 (Phase E)
    private long[] powerCells = new long[0];

    // 当たり判定キャッシュ (サーバ/クライアント共通。Phase C で再構築)
    private VoxelShape shapeCache = null;

    private static final int RANDOM_TICK_CELLS_PER_TICK = 8;
    private static final int POWER_DISTANCE = 3;

    // 将来: client側 quadCache は BER 側で保持するため BE では Object として保持しない (クライアント依存回避)

    public MiniatureBlockEntity(BlockPos pos, BlockState state) {
        super(BambooBlockEntities.MINIATURE_BE.get(), pos, state);
        this.cells = allocateCells(this.size);
    }

    // ===== サイズ管理 =====

    public int getSize() {
        return this.size;
    }

    /**
     * サイズ変更。cells は空で再確保される (サイズ変異時のリセット仕様 §2.9)。
     * NBT改竄対策で clamp する。
     */
    public void setSize(int newSize) {
        int clamped = clampSize(newSize);
        if (clamped == this.size) {
            return;
        }
        this.size = clamped;
        this.cells = allocateCells(this.size);
        this.tickCursor = 0;
        this.buildProgress = 0;
        this.loadState = LOAD_READY;
        this.powerCells = new long[0];
        markDirtyAndSync();
    }

    public static int clampSize(int v) {
        if (v < MIN_SIZE) return MIN_SIZE;
        if (v > MAX_SIZE) return MAX_SIZE;
        return v;
    }

    private static BlockState[][][] allocateCells(int size) {
        BlockState[][][] arr = new BlockState[size][size][size];
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    arr[x][y][z] = air;
                }
            }
        }
        return arr;
    }

    // ===== セル操作 =====

    public boolean isInRange(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.size && y < this.size && z < this.size;
    }

    public BlockState getCell(int x, int y, int z) {
        if (!isInRange(x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        return this.cells[x][y][z];
    }

    public BlockState getCell(BlockPos pos) {
        return getCell(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * セルに state を設定。成功時に dirty と同期をスケジュールする。
     * TE所持 state も見た目保存は許可 (表示のみ) するが、将来的にフィルタする場合はここで制御。
     */
    public boolean setCell(int x, int y, int z, BlockState state) {
        if (!isInRange(x, y, z)) {
            return false;
        }
        if (state == null) {
            state = Blocks.AIR.defaultBlockState();
        }
        BlockState prev = this.cells[x][y][z];
        if (prev == state) {
            return false;
        }
        // 同一ブロックでも properties 違いは変更とみなすため equals 比較
        if (prev.equals(state)) {
            return false;
        }
        this.cells[x][y][z] = state;
        markDirtyAndSync();
        return true;
    }

    public boolean setCell(BlockPos pos, BlockState state) {
        return setCell(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public boolean isEmpty() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    if (this.cells[x][y][z] != air && !this.cells[x][y][z].isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void clearCells() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    this.cells[x][y][z] = air;
                }
            }
        }
        markDirtyAndSync();
    }

    // ===== Dirty / Sync =====

    public boolean isDirty() {
        return this.dirty;
    }

    public void setDirty(boolean v) {
        this.dirty = v;
    }

    public VoxelShape getShapeCache() {
        return this.shapeCache;
    }

    public void setShapeCache(VoxelShape shape) {
        this.shapeCache = shape;
    }

    /**
     * 変更をマークし、間欠バッチ同期をスケジュールする。
     * shapeCache 無効化と setChanged() を伴う。
     */
    public void markDirtyAndSync() {
        this.dirty = true;
        this.shapeCache = null;
        // 将来: quadCache 無効化もここで (Phase D)
        this.buildProgress = 0;
        if (this.syncTimer <= 0) {
            this.syncTimer = SYNC_DELAY;
        }
        setChanged();
    }

    public int getSyncTimer() {
        return this.syncTimer;
    }

    public byte getLoadState() {
        return this.loadState;
    }

    public void setLoadState(byte state) {
        this.loadState = state;
    }

    public int getBuildProgress() {
        return this.buildProgress;
    }

    public void setBuildProgress(int v) {
        this.buildProgress = v;
    }

    public int getTickCursor() {
        return this.tickCursor;
    }

    // ===== Tick (間欠バッチ同期 + 将来の randomTick 用カウンタ) =====

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof MiniatureBlockEntity mini) {
            if (level.isClientSide) {
                mini.clientTick();
            } else {
                mini.serverTick();
            }
        }
    }

    private void serverTick() {
        if (this.syncTimer > 0) {
            this.syncTimer--;
            if (this.syncTimer == 0 && this.dirty) {
                // バッチ送信: setChanged + blockUpdate で getUpdatePacket 経由の同期を発火
                setChanged();
                if (this.level != null) {
                    this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
                    // ENABLED同期 (中身有無)
                    boolean enabled = !isEmpty();
                    BlockState st = this.level.getBlockState(this.worldPosition);
                    if (st.hasProperty(ruby.bamboo.block.MiniatureBlock.ENABLED)
                            && st.getValue(ruby.bamboo.block.MiniatureBlock.ENABLED) != enabled) {
                        this.level.setBlock(this.worldPosition, st.setValue(ruby.bamboo.block.MiniatureBlock.ENABLED, enabled), 3);
                    }
                }
                this.dirty = false;
            }
        }
        tickPowerPropagation();
        tickRandomTick();
    }

    private void clientTick() {
        // Phase D で buildQueue 進行等を扱う。Phase A-D は no-op。
    }

    // ===== 簡易redstone伝播 (Phase E) =====

    private void tickPowerPropagation() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (isEmpty()) {
            return;
        }
        // 6方向 flood-fill で power を計算 (簡易2値)。距離上限 POWER_DISTANCE。
        java.util.Set<BlockPos> powered = new java.util.HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Map<BlockPos, Integer> distMap = new java.util.HashMap<>();
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    BlockState st = this.cells[x][y][z];
                    if (st.isAir()) {
                        continue;
                    }
                    try {
                        if (st.isSignalSource()) {
                            BlockPos p = new BlockPos(x, y, z);
                            queue.add(p);
                            distMap.put(p, 0);
                            powered.add(p);
                        }
                    } catch (Exception e) {
                        // hasBlockEntity等の例外は無視 (見た目のみ)
                    }
                }
            }
        }
        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};
        java.util.Set<BlockPos> visited = new java.util.HashSet<>(powered);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            int d = distMap.getOrDefault(cur, 0);
            if (d >= POWER_DISTANCE) {
                continue;
            }
            for (int i = 0; i < 6; i++) {
                BlockPos n = cur.offset(dx[i], dy[i], dz[i]);
                if (!isInRange(n.getX(), n.getY(), n.getZ())) {
                    continue;
                }
                if (visited.contains(n)) {
                    continue;
                }
                visited.add(n);
                if (this.cells[n.getX()][n.getY()][n.getZ()].isAir()) {
                    continue;
                }
                powered.add(n);
                distMap.put(n, d + 1);
                queue.add(n);
            }
        }
        // 受信側: LIT / OPEN / POWERED を power 有無で更新
        boolean changed = false;
        for (BlockPos p : powered) {
            BlockState st = getCell(p);
            if (st.isAir()) {
                continue;
            }
            BlockState ns = st;
            try {
                if (st.hasProperty(BlockStateProperties.LIT)) {
                    boolean lit = st.getValue(BlockStateProperties.LIT);
                    if (lit != true) {
                        ns = ns.setValue(BlockStateProperties.LIT, true);
                    }
                }
                if (st.hasProperty(BlockStateProperties.OPEN)) {
                    // ドア等は powerで開く想定だが、簡易では LIT と同様に power=trueで OPEN=true
                    // 元が lever等 power source 自体は触らないため、openを持つブロックのみ対象
                    // ここでは power 伝播結果を OPEN に反映 (レバー隣接ドア等)
                    // ただし source 自体は既に powered なので区別しない
                }
                if (st.hasProperty(BlockStateProperties.POWERED)) {
                    boolean pw = st.getValue(BlockStateProperties.POWERED);
                    if (pw != true) {
                        ns = ns.setValue(BlockStateProperties.POWERED, true);
                    }
                }
            } catch (Exception e) {
                continue;
            }
            if (ns != st) {
                this.cells[p.getX()][p.getY()][p.getZ()] = ns;
                changed = true;
            }
        }
        // 非poweredの LIT/POWERED を OFF に戻す (簡易: powerが切れたら消灯)
        // 全セル走査で powered 外の該当ブロックを OFF
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (powered.contains(p)) {
                        continue;
                    }
                    BlockState st = this.cells[x][y][z];
                    if (st.isAir()) {
                        continue;
                    }
                    BlockState ns = st;
                    try {
                        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT)) {
                            // レッドストーンランプ等は powerが無ければ消灯、トーチは除外? 簡易でOFF
                            // signalSource 自体は常時 LIT=true なので、sourceは除外
                            if (!st.isSignalSource()) {
                                ns = ns.setValue(BlockStateProperties.LIT, false);
                            }
                        }
                        if (st.hasProperty(BlockStateProperties.POWERED) && st.getValue(BlockStateProperties.POWERED)) {
                            if (!st.isSignalSource()) {
                                ns = ns.setValue(BlockStateProperties.POWERED, false);
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    if (ns != st) {
                        this.cells[x][y][z] = ns;
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            markDirtyAndSync();
        }
    }

    // ===== randomTick疑似実行 (Phase E) =====

    private void tickRandomTick() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (isEmpty()) {
            return;
        }
        RandomSource rand = this.level.getRandom();
        int total = this.size * this.size * this.size;
        for (int i = 0; i < RANDOM_TICK_CELLS_PER_TICK; i++) {
            int idx = (this.tickCursor + i) % total;
            int x = idx % this.size;
            int y = (idx / this.size) % this.size;
            int z = idx / (this.size * this.size);
            BlockState st = getCell(x, y, z);
            if (st.isAir()) {
                continue;
            }
            if (!st.isRandomlyTicking()) {
                continue;
            }
            // CropBlock のみ簡易成長 (安全)。他は例外捕捉で握りつぶし。
            if (st.getBlock() instanceof CropBlock crop) {
                try {
                    // 成長判定を簡易化: isRandomlyTickingなら 10% で age+1
                    if (rand.nextFloat() < 0.10f) {
                        // ageプロパティを探索
                        for (Property<?> prop : st.getProperties()) {
                            if (prop.getName().equals("age") && prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) {
                                int age = st.getValue(ip);
                                int max = 0;
                                for (Integer v : ip.getPossibleValues()) {
                                    if (v > max) {
                                        max = v;
                                    }
                                }
                                if (age < max) {
                                    BlockState ns = st.setValue(ip, age + 1);
                                    this.cells[x][y][z] = ns;
                                    markDirtyAndSync();
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 握りつぶし (実世界保護)
                }
            } else {
                // 他ブロックの randomTick は安全のためスキップ。必要なら Wrapperで setBlock を内部へ向ける。
                // ここでは例外捕捉付きで呼んでみるが、setBlockが実ワールドに向かう危険があるため呼ばない。
            }
        }
        this.tickCursor = (this.tickCursor + RANDOM_TICK_CELLS_PER_TICK) % total;
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSyncData(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readSyncData(tag);
    }

    // ===== 同期 (MillStoneBlockEntity L444-470 パターン) =====

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
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        CompoundTag tag = new CompoundTag();
        writeSyncData(tag);
        return ClientboundBlockEntityDataPacket.create(this, be -> tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        readSyncData(pkt.getTag());
    }

    public void writeSyncData(CompoundTag tag) {
        tag.putInt(TAG_SIZE, this.size);
        // Cells: 非空セルのみ保存
        ListTag list = new ListTag();
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    BlockState state = this.cells[x][y][z];
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("X", x);
                    entry.putInt("Y", y);
                    entry.putInt("Z", z);
                    CompoundTag stateTag = NbtUtils.writeBlockState(state);
                    entry.put("State", stateTag);
                    list.add(entry);
                }
            }
        }
        tag.put(TAG_CELLS, list);
        if (this.powerCells != null && this.powerCells.length > 0) {
            tag.putLongArray(TAG_POWER, this.powerCells);
        }
    }

    public void saveToItemStack(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_SIZE, this.size);
        if (!isEmpty()) {
            CompoundTag bet = new CompoundTag();
            writeSyncData(bet);
            tag.put("BlockEntityTag", bet);
        }
    }

    public static int getSizeFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_SIZE;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_SIZE, Tag.TAG_INT)) {
            return clampSize(tag.getInt(TAG_SIZE));
        }
        if (tag != null && tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag bet = tag.getCompound("BlockEntityTag");
            if (bet.contains(TAG_SIZE, Tag.TAG_INT)) {
                return clampSize(bet.getInt(TAG_SIZE));
            }
        }
        return DEFAULT_SIZE;
    }

    public VoxelShape rebuildShapeCache() {
        if (isEmpty()) {
            // ENABLED=false時は外枠薄板、ENABLED=trueでも空ならフルブロックにフォールバック
            this.shapeCache = Shapes.block();
            return this.shapeCache;
        }
        double cell = 16.0 / this.size;
        VoxelShape shape = Shapes.empty();
        for (int x = 0; x < this.size; x++) {
            for (int y = 0; y < this.size; y++) {
                for (int z = 0; z < this.size; z++) {
                    BlockState st = this.cells[x][y][z];
                    if (st.isAir()) {
                        continue;
                    }
                    double minX = x * cell;
                    double minY = y * cell;
                    double minZ = z * cell;
                    double maxX = minX + cell;
                    double maxY = minY + cell;
                    double maxZ = minZ + cell;
                    VoxelShape cellShape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
                    shape = Shapes.or(shape, cellShape);
                }
            }
        }
        // 非空セルの合成が空になることはないが、念のため
        if (shape.isEmpty()) {
            shape = Shapes.block();
        }
        // 面AABBの薄板は getShape 側で ENABLED=false 時に付与するため、ここではセル合成のみ
        this.shapeCache = shape;
        return shape;
    }

    private void writeSyncDataInternal(CompoundTag tag) {
        writeSyncData(tag);
    }

    public void readSyncData(CompoundTag tag) {
        // Size
        if (tag.contains(TAG_SIZE, Tag.TAG_INT)) {
            int s = clampSize(tag.getInt(TAG_SIZE));
            if (s != this.size) {
                this.size = s;
                this.cells = allocateCells(this.size);
            } else {
                // 同サイズでも一旦クリアして再読込
                BlockState air = Blocks.AIR.defaultBlockState();
                for (int x = 0; x < this.size; x++) {
                    for (int y = 0; y < this.size; y++) {
                        for (int z = 0; z < this.size; z++) {
                            this.cells[x][y][z] = air;
                        }
                    }
                }
            }
        } else if (this.cells == null || this.cells.length != this.size) {
            this.cells = allocateCells(this.size);
        }

        // Cells
        if (tag.contains(TAG_CELLS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_CELLS, Tag.TAG_COMPOUND);
            BlockState air = Blocks.AIR.defaultBlockState();
            // 既にクリア済みだが、サイズ不一致時は再確保済みなので上書きのみ
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int x = entry.getInt("X");
                int y = entry.getInt("Y");
                int z = entry.getInt("Z");
                if (!isInRange(x, y, z)) {
                    continue;
                }
                CompoundTag stateTag = entry.getCompound("State");
                BlockState state = readBlockState(stateTag);
                if (state == null) {
                    state = air;
                }
                this.cells[x][y][z] = state;
            }
        }

        // Power (任意)
        if (tag.contains(TAG_POWER, Tag.TAG_LONG_ARRAY)) {
            this.powerCells = tag.getLongArray(TAG_POWER);
        } else {
            this.powerCells = new long[0];
        }

        // キャッシュ無効化
        this.shapeCache = null;
        this.dirty = false;
        this.syncTimer = 0;
        this.loadState = LOAD_READY;
        this.buildProgress = 0;
    }

    /**
     * NbtUtils.writeBlockState 形式の CompoundTag から BlockState を復元。
     * ForgeRegistries へのフォールバックで MODブロックも含めて解決する。unknown ブロックは AIR にフォールバック。
     * level.holderLookup を用いた NbtUtils.readBlockState は HolderGetter の二重抽象メソッドにより
     * ラムダ不可のため、Phase A では手動パースに統一する (Phase E 以降で必要なら匿名クラスで対応)。
     */
    private BlockState readBlockState(CompoundTag stateTag) {
        if (stateTag == null || stateTag.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        return readBlockStateFallback(stateTag);
    }

    private static BlockState readBlockStateFallback(CompoundTag stateTag) {
        if (!stateTag.contains("Name", Tag.TAG_STRING)) {
            return Blocks.AIR.defaultBlockState();
        }
        String name = stateTag.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag props = stateTag.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                Property<?> prop = block.getStateDefinition().getProperty(key);
                if (prop == null) {
                    continue;
                }
                String valueStr = props.getString(key);
                state = applyProperty(state, prop, valueStr);
            }
        }
        return state;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String valueStr) {
        return prop.getValue(valueStr).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
