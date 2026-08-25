package ruby.bamboo.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.entity.MiniatureBlockEntity;
import ruby.bamboo.core.config.MiniatureConfig;

/**
 * ミニチュア描画予算マネージャ — ClientTickで内部ブロック数ベースの予算配分を行う。
 * <p>
 * 4096ガラス満杯×6個で30fps割れの実測に対応。非空セル総数で予算化し、近いものから順に
 * 詳細描画を許可、超過分はプレースホルダ(ワイヤー/半透明/非表示)にフォールバック。
 * 予算境界の1個だけ殻LOD(B案)で滑らかに遷移する。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MiniatureRenderManager {

    private static int tickCounter = 0;

    private MiniatureRenderManager() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        int interval = 5;
        try {
            interval = MiniatureConfig.CLIENT.sortInterval.get();
        } catch (Exception e) {
            interval = 5;
        }
        if (interval < 1) interval = 1;
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        updateBudget(mc);
    }

    private static void updateBudget(Minecraft mc) {
        int budget;
        double maxDist;
        boolean lodShell;
        try {
            budget = MiniatureConfig.CLIENT.maxCellsPerFrame.get();
            maxDist = MiniatureConfig.CLIENT.maxRenderDistance.get();
            lodShell = MiniatureConfig.CLIENT.lodBoundaryShell.get();
        } catch (Exception e) {
            budget = 512;
            maxDist = 48.0;
            lodShell = true;
        }
        boolean unlimited = budget <= 0;
        double maxDistSqr = maxDist * maxDist;

        Vec3 camPos;
        try {
            camPos = mc.gameRenderer.getMainCamera().getPosition();
        } catch (Exception e) {
            // フォールバック: プレイヤー位置
            camPos = mc.player.position();
        }

        // スナップショットコピー (WeakHashMap backed, 同期化済みだがiterationはコピーで)
        List<MiniatureBlockEntity> snapshot;
        synchronized (MiniatureBlockEntity.getClientInstances()) {
            snapshot = new ArrayList<>(MiniatureBlockEntity.getClientInstances());
        }
        if (snapshot.isEmpty()) {
            return;
        }

        // 距離でソート
        final Vec3 cam = camPos;
        snapshot.sort(Comparator.comparingDouble(be -> {
            try {
                double dx = be.getBlockPos().getX() + 0.5 - cam.x;
                double dy = be.getBlockPos().getY() + 0.5 - cam.y;
                double dz = be.getBlockPos().getZ() + 0.5 - cam.z;
                return dx * dx + dy * dy + dz * dz;
            } catch (Exception e) {
                return Double.MAX_VALUE;
            }
        }));

        int accumulated = 0;
        boolean shellAssigned = false;

        for (MiniatureBlockEntity be : snapshot) {
            if (be.isRemoved() || be.getLevel() == null) {
                continue;
            }
            double distSqr;
            try {
                double dx = be.getBlockPos().getX() + 0.5 - cam.x;
                double dy = be.getBlockPos().getY() + 0.5 - cam.y;
                double dz = be.getBlockPos().getZ() + 0.5 - cam.z;
                distSqr = dx * dx + dy * dy + dz * dz;
            } catch (Exception e) {
                distSqr = 0;
            }

            // 距離外は常に非詳細 (プレースホルダ)
            if (distSqr > maxDistSqr) {
                be.setRenderActive(false);
                be.setRenderShellOnly(false);
                continue;
            }

            int cnt = be.getNonAirCount();
            // 空は予算消費なし、常にactive(ただしBER側でisEmptyなら描画しない)
            if (cnt <= 0) {
                be.setRenderActive(true);
                be.setRenderShellOnly(false);
                continue;
            }

            if (unlimited) {
                be.setRenderActive(true);
                be.setRenderShellOnly(false);
                continue;
            }

            if (accumulated + cnt <= budget) {
                be.setRenderActive(true);
                be.setRenderShellOnly(false);
                accumulated += cnt;
                continue;
            }

            // 予算超過 — B案: 最初の1個だけ殻LODで救済を試みる
            if (lodShell && !shellAssigned) {
                int shellCnt = countShellNonAir(be);
                // 殻でも予算オーバーなら諦めるが、 accumulated==0 (最初の1個が単体で予算超え)なら殻だけでも表示
                if (accumulated + shellCnt <= budget || accumulated == 0) {
                    be.setRenderActive(true);
                    be.setRenderShellOnly(true);
                    accumulated += shellCnt;
                    shellAssigned = true;
                    continue;
                }
                // 殻でも入らない場合は非表示だが、ワイヤーフレームの方が安いため壳LODを諦めて非activeに
            }

            be.setRenderActive(false);
            be.setRenderShellOnly(false);
        }
    }

    private static int countShellNonAir(MiniatureBlockEntity be) {
        int cnt = 0;
        int size = be.getSize();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (!be.isShellCell(x, y, z)) continue;
                    try {
                        var st = be.getCell(x, y, z);
                        if (st != null && !st.isAir()) cnt++;
                    } catch (Exception e) {
                    }
                }
            }
        }
        return cnt;
    }
}
