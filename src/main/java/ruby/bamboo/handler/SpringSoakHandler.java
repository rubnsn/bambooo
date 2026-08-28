package ruby.bamboo.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.block.SpringBlock;
import ruby.bamboo.block.SpringColor;
import ruby.bamboo.block.SpringWaterBlock;

import java.util.*;

/**
 * 温泉浸漬時の色別ポーション付与。
 * 64tick(3秒)ごとにワールド時間のビット演算で判定し、近い色3色を同一グループとして5種の良性効果、
 * 2色混合で希少効果、3色以上混合で poison、無色(DEFAULT)はHP回復。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpringSoakHandler {

    private SpringSoakHandler() {}

    // 5グループ定義（近い色3色ずつ）
    // G0 mono   : WHITE, LIGHT_GRAY, GRAY        -> RESISTANCE
    // G1 warm   : RED, ORANGE, BROWN             -> FIRE_RESISTANCE
    // G2 yellow : YELLOW, LIME, GREEN            -> DIG_SPEED (haste)
    // G3 blue   : LIGHT_BLUE, CYAN, BLUE         -> WATER_BREATHING
    // G4 purple : MAGENTA, PURPLE, PINK          -> NIGHT_VISION
    private static final Map<SpringColor, MobEffect> GROUP_EFFECT = new EnumMap<>(SpringColor.class);
    private static final Map<SpringColor, Integer> COLOR_GROUP = new EnumMap<>(SpringColor.class);
    private static final List<MobEffect> RARE_EFFECTS = List.of(
            MobEffects.REGENERATION,
            MobEffects.HEALTH_BOOST,
            MobEffects.ABSORPTION,
            MobEffects.SATURATION,
            MobEffects.LUCK,
            MobEffects.SLOW_FALLING,
            MobEffects.CONDUIT_POWER,
            MobEffects.DOLPHINS_GRACE
    );

    static {
        // group 0 mono
        putGroup(new SpringColor[]{SpringColor.WHITE, SpringColor.LIGHT_GRAY, SpringColor.GRAY}, 0, MobEffects.DAMAGE_RESISTANCE);
        // group 1 warm
        putGroup(new SpringColor[]{SpringColor.RED, SpringColor.ORANGE, SpringColor.BROWN}, 1, MobEffects.FIRE_RESISTANCE);
        // group 2 yellow-green
        putGroup(new SpringColor[]{SpringColor.YELLOW, SpringColor.LIME, SpringColor.GREEN}, 2, MobEffects.DIG_SPEED);
        // group 3 blue
        putGroup(new SpringColor[]{SpringColor.LIGHT_BLUE, SpringColor.CYAN, SpringColor.BLUE}, 3, MobEffects.WATER_BREATHING);
        // group 4 purple
        putGroup(new SpringColor[]{SpringColor.MAGENTA, SpringColor.PURPLE, SpringColor.PINK}, 4, MobEffects.NIGHT_VISION);
        // DEFAULT / VANILLA / BLACK はグループ外（heal）
    }

    private static void putGroup(SpringColor[] colors, int gid, MobEffect eff) {
        for (SpringColor c : colors) {
            GROUP_EFFECT.put(c, eff);
            COLOR_GROUP.put(c, gid);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide) return;
        // 64tickごとに判定（ビット演算で剰余より軽量）
        if ((level.getGameTime() & 63L) != 0L) return;
        if (!entity.isAlive() || entity.isSpectator()) return;
        // ポーション効果を受けない生物は除外（ArmorStandなどは canBeAffected false だがここではLiving全般）
        // ただしアンデッド等の除外はしない

        // 温泉流体に浸かっているか
        BlockPos pos = entity.blockPosition();
        // 足元と目線の両方をチェック（泳いでいる場合）
        boolean inSpring = isSpringFluid(level, pos);
        BlockPos eyePos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        if (!inSpring) {
            inSpring = isSpringFluid(level, eyePos);
            if (inSpring) pos = eyePos;
        }
        if (!inSpring) return;

        // 色の収集（3x3で軽量サンプリング、64tickごとなので高負荷回避）
        Set<SpringColor> distinct = new HashSet<>();
        // ブレンド用のカウント（variance判定にも使う）
        // サンプリング範囲 3x3 (dx,dz -1..1)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = pos.offset(dx, 0, dz);
                SpringColor col = getSpringColorAt(level, p);
                if (col != null) distinct.add(col);
            }
        }
        // distinctが空＝情報なし（通常ありえない）→ heal
        if (distinct.isEmpty()) {
            heal(entity);
            return;
        }
        // BLACKはDEFAULT扱い（リセット）なので除去してDEFAULTに置換
        if (distinct.contains(SpringColor.BLACK)) {
            distinct.remove(SpringColor.BLACK);
            distinct.add(SpringColor.DEFAULT);
        }
        // VANILLAもDEFAULT同等
        if (distinct.contains(SpringColor.VANILLA)) {
            distinct.remove(SpringColor.VANILLA);
            distinct.add(SpringColor.DEFAULT);
        }

        // 単色または同グループ内の複数色は純色として扱う
        // distinctが1種のみ、または複数だが全て同一グループなら pure
        Set<Integer> groups = new HashSet<>();
        boolean hasDefault = false;
        for (SpringColor c : distinct) {
            if (c == SpringColor.DEFAULT) hasDefault = true;
            else {
                Integer g = COLOR_GROUP.get(c);
                if (g != null) groups.add(g);
            }
        }

        // DEFAULT単独 → heal
        if (distinct.size() == 1 && hasDefault) {
            heal(entity);
            return;
        }
        // 全てDEFAULTのみ（hasDefaultでgroups empty）の場合も heal
        if (groups.isEmpty() && hasDefault) {
            heal(entity);
            return;
        }
        // DEFAULT混じりの混合は「色が混ざっている」とみなす
        // 例: DEFAULT + 1グループ色 → 2グループ混合として希少扱い（hasDefaultをグループとして数える）
        int groupCount = groups.size() + (hasDefault ? 1 : 0);
        int colorCount = distinct.size();

        if (groupCount == 1) {
            // 純色または同グループ内の複数色 → そのグループの効果
            // 代表色は distinct のうちDEFAULT以外で最初
            SpringColor rep = null;
            for (SpringColor c : distinct) if (c != SpringColor.DEFAULT) { rep = c; break; }
            if (rep == null) { heal(entity); return; }
            MobEffect eff = GROUP_EFFECT.get(rep);
            if (eff != null) apply(entity, eff);
            else heal(entity);
            return;
        }
        if (groupCount == 2) {
            // 2グループ混合 → 希少効果
            // 2グループの組み合わせで希少プールから決定（hashで安定）
            int hash = 0;
            for (int g : groups) hash = hash * 31 + g;
            if (hasDefault) hash = hash * 31 + 99;
            int idx = Math.abs(hash) % RARE_EFFECTS.size();
            MobEffect rare = RARE_EFFECTS.get(idx);
            apply(entity, rare);
            return;
        }
        // 3グループ以上または4色以上でごちゃ混ぜ → poison
        if (groupCount >= 3 || colorCount >= 4) {
            apply(entity, MobEffects.POISON, 100, 0);
            return;
        }
        // フォールバックは希少
        apply(entity, MobEffects.REGENERATION);
    }

    private static boolean isSpringFluid(Level level, BlockPos pos) {
        var f = level.getFluidState(pos);
        return f.getType() == BambooMod.SPRING_WATER_SOURCE.get() || f.getType() == BambooMod.SPRING_WATER_FLOWING.get();
    }

    private static SpringColor getSpringColorAt(Level level, BlockPos pos) {
        // ブロックが spring_water ならその源泉の色、そうでなくても流体が spring なら近傍から借用
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof SpringWaterBlock) {
            BlockPos src = SpringWaterBlock.findSource(level, pos, st, 32);
            if (src != null) {
                BlockState srcSt = level.getBlockState(src);
                if (srcSt.getBlock() instanceof SpringBlock) return srcSt.getValue(SpringBlock.COLOR);
            }
            return SpringColor.DEFAULT;
        }
        // 水没など: 流体が spring なら周囲の spring_water ブロックから色を推測
        var f = level.getFluidState(pos);
        if (f.getType() == BambooMod.SPRING_WATER_SOURCE.get() || f.getType() == BambooMod.SPRING_WATER_FLOWING.get()) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (d == net.minecraft.core.Direction.UP) continue;
                BlockPos n = pos.relative(d);
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof SpringWaterBlock) {
                    BlockPos src = SpringWaterBlock.findSource(level, n, ns, 32);
                    if (src != null) {
                        BlockState srcSt = level.getBlockState(src);
                        if (srcSt.getBlock() instanceof SpringBlock) return srcSt.getValue(SpringBlock.COLOR);
                    }
                    return SpringColor.DEFAULT;
                }
            }
            return SpringColor.DEFAULT;
        }
        return null;
    }

    private static void heal(LivingEntity e) {
        // ハート1個(2HP)回復、最大超えない
        if (e.getHealth() < e.getMaxHealth()) {
            e.heal(2.0F);
        }
    }

    private static void apply(LivingEntity e, MobEffect eff) {
        apply(e, eff, 6000, 0);
    }

    private static void apply(LivingEntity e, MobEffect eff, int duration, int amp) {
        // 既に同効果がより長く残っていれば更新しない（チラつき防止）
        var existing = e.getEffect(eff);
        if (existing != null && existing.getDuration() > 100) return;
        e.addEffect(new MobEffectInstance(eff, duration, amp, false, true, true));
    }
}
