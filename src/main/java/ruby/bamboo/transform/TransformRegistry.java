package ruby.bamboo.transform;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 変身先の静的定義。描画方式と能力グループのみを保持する。
 * 願い文言の一致ルールは datapack (data/bamboomod/bamboo_wish/transform.json) に、
 * リザルトのセリフは lang (bamboomod.wish.result.transform.*) に外だししている。
 */
public final class TransformRegistry {

    /** 描画方式。TRUE=真モデル / GROUND=併用・足接地 / FLOAT=併用・頭胸高さ浮遊 / HEAD=頭部のみ。 */
    public enum RenderMode {
        TRUE,
        HYBRID_GROUND,
        HYBRID_FLOAT,
        HEAD
    }

    public record RaceConfig(RenderMode mode, float yOff, float scale) {
    }

    private static final Map<String, RaceConfig> CONFIGS = new HashMap<>();

    /** 日光で炎上する種(ヘルメットで防げる)。 */
    public static final Set<String> SUN_BURN = new HashSet<>();
    /** 弓の矢を消費しない種。 */
    public static final Set<String> BOW_FREE = new HashSet<>();
    /** クロスボウの矢を消費しない種。 */
    public static final Set<String> XBOW_FREE = new HashSet<>();
    /** 金防具比例バフの種。 */
    public static final Set<String> GOLD_SCALE = new HashSet<>();
    /** 疑似滑空できる種。 */
    public static final Set<String> GLIDE = new HashSet<>();
    /** 火・溶岩ダメージを受けない種。 */
    public static final Set<String> FIRE_IMMUNE = new HashSet<>();
    /** 冠水で痛がる種。 */
    public static final Set<String> WATER_HURT = new HashSet<>();
    /** 水中呼吸+水中暗視の種。 */
    public static final Set<String> AQUATIC = new HashSet<>();
    /** 毒無効の種。 */
    public static final Set<String> POISON_IMMUNE = new HashSet<>();
    /** クモの巣を軽減する種。 */
    public static final Set<String> WEB_RESIST = new HashSet<>();
    /** 肉しか食べられない種。 */
    public static final Set<String> CARNIVORE = new HashSet<>();
    /** 肉が食べられない種。 */
    public static final Set<String> HERBIVORE = new HashSet<>();
    /** 花以外食べられない種。 */
    public static final Set<String> FLOWER_ONLY = new HashSet<>();
    /** 移動速度倍率 (既定1.0)。 */
    public static final Map<String, Double> SPEED = new HashMap<>();
    /** 最大体力加算 (ハート換算ではなくHP値)。 */
    public static final Map<String, Double> HEALTH = new HashMap<>();
    /** 攻撃力加算。 */
    public static final Map<String, Double> ATTACK = new HashMap<>();
    /** ノックバック耐性加算 (0.0-1.0)。 */
    public static final Map<String, Double> KNOCKBACK = new HashMap<>();
    /** 被ダメージ倍率 (既定1.0)。 */
    public static final Map<String, Double> DAMAGE_TAKEN = new HashMap<>();
    /** 与ダメージ軽減率 (0.0-1.0、既定0.0)。 */
    public static final Map<String, Double> DAMAGE_CUT = new HashMap<>();
    /** 空腹の減り倍率 (既定1.0)。 */
    public static final Map<String, Double> HUNGER = new HashMap<>();

    private TransformRegistry() {
    }

    private static void race(String id, RenderMode mode, float yOff, float scale) {
        CONFIGS.put(id, new RaceConfig(mode, yOff, scale));
    }

    static {
        // ===== 二足・真モデル =====
        race("minecraft:zombie", RenderMode.TRUE, 0F, 1F);
        race("minecraft:husk", RenderMode.TRUE, 0F, 1F);
        race("minecraft:drowned", RenderMode.TRUE, 0F, 1F);
        race("minecraft:skeleton", RenderMode.TRUE, 0F, 1F);
        race("minecraft:stray", RenderMode.TRUE, 0F, 1F);
        race("minecraft:wither_skeleton", RenderMode.TRUE, 0F, 1F);
        race("minecraft:zombie_villager", RenderMode.TRUE, 0F, 1F);
        race("minecraft:piglin", RenderMode.TRUE, 0F, 1F);
        race("minecraft:piglin_brute", RenderMode.TRUE, 0F, 1F);
        race("minecraft:zombified_piglin", RenderMode.TRUE, 0F, 1F);
        race("minecraft:enderman", RenderMode.TRUE, 0F, 1F);
        race("minecraft:witch", RenderMode.TRUE, 0F, 1F);
        race("minecraft:evoker", RenderMode.TRUE, 0F, 1F);
        race("minecraft:vindicator", RenderMode.TRUE, 0F, 1F);
        race("minecraft:pillager", RenderMode.TRUE, 0F, 1F);
        race("minecraft:villager", RenderMode.TRUE, 0F, 1F);
        race("minecraft:iron_golem", RenderMode.TRUE, 0F, 0.65F);
        // ===== 併用・足接地 =====
        race("minecraft:creeper", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:pig", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:cow", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:sheep", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:wolf", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:cat", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:ocelot", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:fox", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:horse", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:spider", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:cave_spider", RenderMode.HYBRID_GROUND, 0F, 0.8F);
        race("minecraft:endermite", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:silverfish", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:goat", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:panda", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:turtle", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:hoglin", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:zoglin", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:slime", RenderMode.HYBRID_GROUND, 0F, 0.7F);
        race("minecraft:magma_cube", RenderMode.HYBRID_GROUND, 0F, 0.7F);
        race("minecraft:strider", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:chicken", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:rabbit", RenderMode.HYBRID_GROUND, 0F, 1F);
        race("minecraft:snow_golem", RenderMode.HYBRID_GROUND, 0F, 1F);
        // ===== 併用・浮遊 =====
        race("minecraft:phantom", RenderMode.HYBRID_FLOAT, 1.0F, 0.9F);
        race("minecraft:blaze", RenderMode.HYBRID_FLOAT, 0F, 1F);
        race("minecraft:ghast", RenderMode.HYBRID_FLOAT, 1.0F, 0.2F);
        race("minecraft:vex", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:allay", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:bee", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:parrot", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:bat", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:guardian", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:elder_guardian", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:squid", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:glow_squid", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:axolotl", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        race("minecraft:frog", RenderMode.HYBRID_FLOAT, 0.6F, 1F);
        race("minecraft:dolphin", RenderMode.HYBRID_FLOAT, 1.0F, 1F);
        // ===== 頭部のみ =====
        race("minecraft:shulker", RenderMode.HEAD, 1.25F, 0.9F);
        race("minecraft:warden", RenderMode.HEAD, 1.25F, 0.7F);
        race("minecraft:ravager", RenderMode.HEAD, 1.2F, 0.7F);

        // ===== 能力グループ =====
        Collections.addAll(SUN_BURN, "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
                "minecraft:zombie_villager", "minecraft:zombified_piglin", "minecraft:zoglin",
                "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton");
        Collections.addAll(BOW_FREE, "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton");
        Collections.addAll(XBOW_FREE, "minecraft:pillager");
        Collections.addAll(GOLD_SCALE, "minecraft:piglin", "minecraft:piglin_brute");
        Collections.addAll(GLIDE, "minecraft:phantom", "minecraft:bee", "minecraft:parrot",
                "minecraft:bat", "minecraft:chicken");
        Collections.addAll(FIRE_IMMUNE, "minecraft:blaze", "minecraft:magma_cube", "minecraft:strider");
        Collections.addAll(WATER_HURT, "minecraft:blaze", "minecraft:magma_cube", "minecraft:strider");
        Collections.addAll(AQUATIC, "minecraft:guardian", "minecraft:elder_guardian", "minecraft:squid",
                "minecraft:glow_squid", "minecraft:axolotl", "minecraft:frog", "minecraft:dolphin");
        Collections.addAll(POISON_IMMUNE, "minecraft:cave_spider");
        Collections.addAll(WEB_RESIST, "minecraft:spider", "minecraft:cave_spider");
        Collections.addAll(CARNIVORE, "minecraft:wolf", "minecraft:fox", "minecraft:cat", "minecraft:ocelot");
        Collections.addAll(HERBIVORE, "minecraft:cow", "minecraft:sheep", "minecraft:goat", "minecraft:horse",
                "minecraft:panda", "minecraft:rabbit");
        Collections.addAll(FLOWER_ONLY, "minecraft:bee");

        SPEED.put("minecraft:wolf", 1.2D);
        SPEED.put("minecraft:fox", 1.2D);
        SPEED.put("minecraft:cat", 1.1D);
        SPEED.put("minecraft:ocelot", 1.1D);
        SPEED.put("minecraft:horse", 1.5D);
        SPEED.put("minecraft:rabbit", 1.5D);
        HEALTH.put("minecraft:witch", -4D);
        HEALTH.put("minecraft:vex", -8D);
        HEALTH.put("minecraft:allay", -8D);
        HEALTH.put("minecraft:bee", -8D);
        HEALTH.put("minecraft:parrot", -8D);
        HEALTH.put("minecraft:bat", -8D);
        HEALTH.put("minecraft:cave_spider", -4D);
        HEALTH.put("minecraft:endermite", -10D);
        HEALTH.put("minecraft:silverfish", -10D);
        HEALTH.put("minecraft:frog", -10D);
        HEALTH.put("minecraft:chicken", -10D);
        HEALTH.put("minecraft:rabbit", -10D);
        HEALTH.put("minecraft:axolotl", -6D);
        HEALTH.put("minecraft:ravager", 10D);
        HEALTH.put("minecraft:iron_golem", 20D);
        HEALTH.put("minecraft:snow_golem", 10D);
        ATTACK.put("minecraft:vindicator", 2D);
        ATTACK.put("minecraft:vex", 2D);
        KNOCKBACK.put("minecraft:hoglin", 0.6D);
        KNOCKBACK.put("minecraft:ravager", 0.5D);
        KNOCKBACK.put("minecraft:warden", 1.0D);
        DAMAGE_TAKEN.put("minecraft:skeleton", 2D);
        DAMAGE_TAKEN.put("minecraft:stray", 2D);
        DAMAGE_TAKEN.put("minecraft:wither_skeleton", 2D);
        DAMAGE_TAKEN.put("minecraft:turtle", 0.5D);
        DAMAGE_CUT.put("minecraft:iron_golem", 0.25D);
        HUNGER.put("minecraft:piglin", 1.25D);
        HUNGER.put("minecraft:piglin_brute", 1.5D);
        HUNGER.put("minecraft:iron_golem", 0.5D);
        HUNGER.put("minecraft:snow_golem", 0.5D);
    }

    public static RaceConfig configOf(String entityId) {
        RaceConfig c = CONFIGS.get(entityId);
        return c != null ? c : new RaceConfig(RenderMode.HYBRID_GROUND, 0F, 1F);
    }

    public static boolean isAdopted(String entityId) {
        return CONFIGS.containsKey(entityId);
    }

    /** EntityType が変身先として有効か(プレイヤー除外のみ。村人・ゴーレムは MISC 分類なので弾かない)。 */
    public static boolean isValidType(EntityType<?> type) {
        return type != null && type != EntityType.PLAYER;
    }

    private static final Map<String, Boolean> LIVING_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 実体が LivingEntity かを生成テストで確認する。村人・ゴーレム等の MISC 生き物を通すため。
     * 結果はキャッシュされるので毎 tick 呼ばれても軽い。
     */
    public static boolean isLivingId(String id, ServerLevel level) {
        if (id == null || id.isEmpty() || level == null) {
            return false;
        }
        Boolean cached = LIVING_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
            if (isValidType(type)) {
                ok = type.create(level) instanceof LivingEntity;
            }
        } catch (Exception ignored) {
        }
        LIVING_CACHE.put(id, ok);
        return ok;
    }
}
