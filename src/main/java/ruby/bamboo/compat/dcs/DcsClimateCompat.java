package ruby.bamboo.compat.dcs;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * HeatAndClimate (dcs_climate) との任意連携の土台。JEI同様、H&C不在でもBamboo単体で動作する。
 *
 * <p>環境非依存のため {@code defeatedcrow.hac.*} への直接参照 (import) を持たない。
 * ClimateAPI等を読む将来の実装はリフレクション経由で行い、このクラス経由で分岐すること。</p>
 */
public final class DcsClimateCompat {
    public static final String MODID = "dcs_climate";

    private DcsClimateCompat() {}

    /** H&Cがロードされている場合のみ true。mods.tomlの任意依存と対になる実行時分岐用。 */
    public static boolean isLoaded() {
        try {
            return ModList.get().isLoaded(MODID);
        } catch (Exception e) {
            return false;
        }
    }

    // ===== プランター用: 開花状態の解決 =====

    /** {@code FlowerPotEntity.getRenderState(ItemStack)} のキャッシュ。null=未解決or不在。 */
    private static volatile Method flowerRenderState;
    private static volatile boolean flowerRenderStateResolved;

    /**
     * HaCの花アイテムに対応する開花 {@link BlockState} を返す。H&C不在・解決失敗・マップ外は empty。
     * 描画は呼び出し側のバニラ経路 ({@code BlockRenderer#renderSingleBlock}) をそのまま使うこと。
     */
    public static Optional<BlockState> getPottedFlowerState(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded()) return Optional.empty();
        try {
            Method m = flowerRenderState;
            if (m == null && !flowerRenderStateResolved) {
                synchronized (DcsClimateCompat.class) {
                    m = flowerRenderState;
                    if (m == null && !flowerRenderStateResolved) {
                        try {
                            Class<?> cls = Class.forName("defeatedcrow.hac.core.material.entity.FlowerPotEntity");
                            m = cls.getMethod("getRenderState", ItemStack.class);
                            flowerRenderState = m;
                        } catch (Exception ignored) {
                            // H&Cの版違い等では開花解決なしで続行
                        } finally {
                            flowerRenderStateResolved = true;
                        }
                    }
                }
            }
            if (m == null) return Optional.empty();
            Object ret = m.invoke(null, stack);
            return ret instanceof BlockState state ? Optional.of(state) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** {@code LeavesCropBlockDC} のキャッシュ。null=未解決or不在。 */
    private static volatile Class<?> leavesCropBlockClass;
    private static volatile boolean leavesCropBlockResolved;

    /**
     * HaC花瓶と同様に小さく描画すべき葉物花か。バニラ {@code LeavesBlock} または
     * HaCの {@code LeavesCropBlockDC} 系（ツバキ・サクラ等の樹木花）の場合 true。
     * H&C不在・解決失敗時は false（通常スケール）。
     */
    public static boolean isLeafyFlower(BlockState state) {
        if (state == null) return false;
        try {
            if (state.getBlock() instanceof LeavesBlock) return true;
            if (!isLoaded()) return false;
            Class<?> cls = leavesCropBlockClass;
            if (cls == null && !leavesCropBlockResolved) {
                synchronized (DcsClimateCompat.class) {
                    cls = leavesCropBlockClass;
                    if (cls == null && !leavesCropBlockResolved) {
                        try {
                            cls = Class.forName("defeatedcrow.hac.food.material.block.crops.LeavesCropBlockDC");
                            leavesCropBlockClass = cls;
                        } catch (Exception ignored) {
                            // 版違い等では葉物判定なしで続行
                        } finally {
                            leavesCropBlockResolved = true;
                        }
                    }
                }
            }
            return cls != null && cls.isInstance(state.getBlock());
        } catch (Exception ignored) {
            return false;
        }
    }
}
