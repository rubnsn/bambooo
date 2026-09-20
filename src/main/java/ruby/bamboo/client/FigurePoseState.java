package ruby.bamboo.client;

import javax.annotation.Nullable;

import net.minecraft.client.model.EntityModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * フィギュア用ポーズの一時受け渡し (クライアントのみ)。
 * FigureRenderer が vanilla 描画へ委譲する間だけ ThreadLocal に保持し、
 * FigurePoseMixin が setupAnim 直後に適用する。通常描画には一切干渉しない。
 */
@OnlyIn(Dist.CLIENT)
public final class FigurePoseState {

    private FigurePoseState() {
    }

    private static final ThreadLocal<CompoundTag> PENDING = new ThreadLocal<>();

    /** 適用前の角度 (共有モデルへの書きっぱなし漏れを防ぐため描画後に戻す)。同一部位は初回のみ退避する */
    private static final java.util.Map<net.minecraft.client.model.geom.ModelPart, Saved> SAVED =
            new java.util.LinkedHashMap<>();

    private record Saved(net.minecraft.client.model.geom.ModelPart part, float x, float y,
            float z) {
    }

    public static void set(@Nullable CompoundTag pose) {
        if (pose == null || pose.isEmpty()) {
            PENDING.remove();
        } else {
            PENDING.set(pose);
        }
    }

    public static void clear() {
        restoreAll();
        PENDING.remove();
    }

    /** 退避した角度を全て戻す (通常Entityへの漏れ防止)。 */
    public static void restoreAll() {
        if (SAVED.isEmpty()) return;
        try {
            for (Saved s : SAVED.values()) {
                try {
                    s.part().setRotation(s.x(), s.y(), s.z());
                } catch (Exception ignored) {
                }
            }
        } finally {
            SAVED.clear();
        }
    }

    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    public static void applyPending(EntityModel<?> model) {
        CompoundTag pose = PENDING.get();
        if (pose == null || pose.isEmpty()) return;
        try {
            // モデルは描画系の共有インスタンスのため、適用前に退避する。
            // 本体と層で同クラスの場合に二重発火するため、初回のみ (二重目は汚染値を掴む)
            for (FigurePose.Part part : FigurePose.discover(model)) {
                var p = part.part();
                SAVED.putIfAbsent(p, new Saved(p, p.xRot, p.yRot, p.zRot));
            }
            FigurePose.apply(model, pose);
            // 注入の生存証明 (同一キーセットでは初回のみ。毎フレーム出さないため)
            if (!pose.isEmpty()) {
                String key = model.getClass().getName() + pose.getAllKeys();
                synchronized (LOGGED) {
                    if (LOGGED.add(key)) {
                        ruby.bamboo.BambooMod.LOGGER.info(
                                "[FigurePose] injected into {} keys={}",
                                model.getClass().getSimpleName(), pose.getAllKeys());
                    }
                }
            }
        } catch (Exception e) {
            // 適用失敗時は素通し
        }
    }
}
