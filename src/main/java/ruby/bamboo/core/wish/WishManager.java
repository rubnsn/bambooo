package ruby.bamboo.core.wish;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import ruby.bamboo.core.config.WishConfig;
import ruby.bamboo.util.WishEntitySearch;
import ruby.bamboo.util.WishItemSearch;
import ruby.bamboo.util.WishNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 願いの解釈と実行。常時サーバ側で実行。
 * port-spec-wish §4 準拠。
 */
public final class WishManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static List<WishEntry> ENTRIES = new ArrayList<>();
    private static List<String> PREFIXES = new ArrayList<>(List.of("アイテム", "あいてむ", "item"));

    private WishManager() {
    }

    public static synchronized void setEntries(List<WishEntry> entries, List<String> prefixes) {
        ENTRIES = new ArrayList<>(entries);
        PREFIXES = new ArrayList<>(prefixes);
        LOGGER.info("WishManager loaded {} entries, prefixes {}", ENTRIES.size(), PREFIXES);
    }

    public static synchronized List<WishEntry> getEntries() {
        return new ArrayList<>(ENTRIES);
    }

    public static synchronized List<String> getPrefixes() {
        return new ArrayList<>(PREFIXES);
    }

    public static void resolveAndExecute(ServerPlayer player, String rawInput) {
        if (player == null) return;
        String normalized = WishNormalizer.normalize(rawInput);
        normalized = normalized.replaceAll("\\p{Cntrl}", "");
        if (normalized.length() > 30) normalized = normalized.substring(0, 30);
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            fallback(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();

        // 1. priority entries first
        List<WishEntry> priorityHits = new ArrayList<>();
        synchronized (WishManager.class) {
            for (WishEntry e : ENTRIES) {
                if (!e.priority) continue;
                // punishGreed config: if greed punishment disabled, skip punishment entries
                if (isPunishment(e) && !WishConfig.COMMON.punishGreed.get()) continue;
                if (e.matches(normalized)) priorityHits.add(e);
            }
        }
        if (!priorityHits.isEmpty()) {
            WishEntry chosen = weightedRandom(priorityHits, random);
            executeEntry(player, chosen, random);
            return;
        }

        // 2. normal entries
        List<WishEntry> hits = new ArrayList<>();
        synchronized (WishManager.class) {
            for (WishEntry e : ENTRIES) {
                if (e.priority) continue;
                if (e.matches(normalized)) hits.add(e);
            }
        }
        if (!hits.isEmpty()) {
            WishEntry chosen = weightedRandom(hits, random);
            executeEntry(player, chosen, random);
            return;
        }

        // 3. entity name direct summon (動物/モンスター名をそのまま入力)
        EntityType<?> hitType = WishEntitySearch.find(normalized, level);
        if (hitType != null) {
            summonEntityType(player, hitType, 1);
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            return;
        }

        // 4. item prefix decomposition
        String searchTerm = extractItemSearchTerm(normalized);
        if (searchTerm != null) {
            if (searchTerm.isEmpty()) {
                fallback(player);
                return;
            }
            Item item = WishItemSearch.findItem(WishNormalizer.normalize(searchTerm));
            if (item != null) {
                giveItemWithAutoEnchant(player, item, 1, null, random, "bamboomod.wish.result.generic");
                return;
            }
            fallback(player);
            return;
        }

        // 5. fallback
        fallback(player);
    }

    private static boolean isPunishment(WishEntry e) {
        for (WishEffect eff : e.effects) {
            if ("punishment".equalsIgnoreCase(eff.type)) return true;
        }
        return false;
    }

    private static String extractItemSearchTerm(String normalizedInput) {
        List<String> prefixes;
        synchronized (WishManager.class) {
            prefixes = new ArrayList<>(PREFIXES);
        }
        for (String alias : prefixes) {
            String normAlias = WishNormalizer.normalize(alias);
            if (normAlias.isEmpty()) continue;
            if (normalizedInput.equals(normAlias)) {
                return "";
            }
            if (normalizedInput.startsWith(normAlias + " ")) {
                String rest = normalizedInput.substring(normAlias.length()).trim();
                return rest;
            }
        }
        return null;
    }

    private static WishEntry weightedRandom(List<WishEntry> list, RandomSource random) {
        int total = 0;
        for (WishEntry e : list) total += e.weight;
        if (total <= 0) return list.get(0);
        int r = random.nextInt(total);
        int acc = 0;
        for (WishEntry e : list) {
            acc += e.weight;
            if (r < acc) return e;
        }
        return list.get(list.size() - 1);
    }

    private static void executeEntry(ServerPlayer player, WishEntry entry, RandomSource random) {
        LOGGER.info("Wish matched {} for {}: '{}' -> {}", entry.id, player.getName().getString(), entry.pattern, entry.effects.size());
        for (WishEffect eff : entry.effects) {
            executeEffect(player, eff, random);
        }
        if (entry.message != null && !entry.message.isEmpty()) {
            player.displayClientMessage(Component.translatable(entry.message).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            // default generic if not specified and effect was give_item etc? keep generic
            // Only for give_item we already send message inside giveItem; avoid duplicate
            // If entry has no message but we want generic, do nothing extra
        }
    }

    private static void executeEffect(ServerPlayer player, WishEffect eff, RandomSource random) {
        ServerLevel level = player.serverLevel();
        String type = eff.type;
        try {
            switch (type) {
                case "give_item" -> {
                    String itemStr = eff.args.has("item") ? eff.args.get("item").getAsString() : "";
                    int count = eff.args.has("count") ? eff.args.get("count").getAsInt() : 1;
                    Boolean enchanted = null;
                    if (eff.args.has("enchanted")) enchanted = eff.args.get("enchanted").getAsBoolean();
                    ResourceLocation rl = new ResourceLocation(itemStr);
                    Item item = ForgeRegistries.ITEMS.getValue(rl);
                    if (item == null) {
                        LOGGER.warn("Unknown item {}", itemStr);
                        break;
                    }
                    giveItemWithAutoEnchant(player, item, count, enchanted, random, null);
                }
                case "xp" -> {
                    int levels = eff.args.has("levels") ? eff.args.get("levels").getAsInt() : 30;
                    player.giveExperienceLevels(levels);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                case "effect_potion" -> {
                    String effectStr = eff.args.has("effect") ? eff.args.get("effect").getAsString() : "";
                    int duration = eff.args.has("duration") ? eff.args.get("duration").getAsInt() : 200;
                    int amp = eff.args.has("amplifier") ? eff.args.get("amplifier").getAsInt() : 0;
                    ResourceLocation rl = new ResourceLocation(effectStr);
                    MobEffect me = ForgeRegistries.MOB_EFFECTS.getValue(rl);
                    if (me != null) {
                        player.addEffect(new MobEffectInstance(me, duration, amp));
                    }
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                case "teleport_random" -> {
                    int range = eff.args.has("range") ? eff.args.get("range").getAsInt() : 64;
                    teleportRandom(player, range, random);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                case "summon" -> {
                    String entityStr = eff.args.has("entity") ? eff.args.get("entity").getAsString() : "";
                    int cnt = eff.args.has("count") ? eff.args.get("count").getAsInt() : 1;
                    summon(player, entityStr, cnt);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                case "weather" -> {
                    String kind = eff.args.has("kind") ? eff.args.get("kind").getAsString() : "clear";
                    int dur = eff.args.has("duration") ? eff.args.get("duration").getAsInt() : 6000;
                    applyWeather(level, kind, dur);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                case "punishment" -> {
                    String kind = eff.args.has("kind") ? eff.args.get("kind").getAsString() : "lightning";
                    punish(player, kind);
                }
                case "heal" -> {
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().eat(20, 20);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                default -> LOGGER.warn("Unknown wish effect type {}", type);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to execute wish effect {}", type, ex);
        }
    }

    private static void giveItemWithAutoEnchant(ServerPlayer player, Item item, int count, Boolean enchantedFlag, RandomSource random, String messageKey) {
        ItemStack stack = new ItemStack(item, count);
        boolean enchanted = enchantedFlag != null ? enchantedFlag : stack.getMaxDamage() > 0;
        boolean over = false;
        if (enchanted && stack.isEnchantable()) {
            stack = EnchantmentHelper.enchantItem(random, stack, 30, true);
            // 低確率でエンチャントテーブル最大値+1 が宿る（Elonaの「神」の祝福的なもの）
            int overChance = WishConfig.COMMON.overenchantChance.get();
            if (overChance > 0 && random.nextInt(overChance) == 0) {
                over = tryOverEnchant(stack, random);
            }
        }
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 10;
        double z = player.getZ();
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDeltaMovement(0, 0, 0);
        entity.setPickUpDelay(10);
        level.addFreshEntity(entity);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    /**
     * 既存エンチャントのどれか1件を最大レベル+1に引き上げる。
     * @return 成功したら true（エンチャントが1つも無ければ false）
     */
    private static boolean tryOverEnchant(ItemStack stack, RandomSource random) {
        java.util.Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        if (map.isEmpty()) return false;
        List<Enchantment> keys = new ArrayList<>(map.keySet());
        Enchantment ench = keys.get(random.nextInt(keys.size()));
        stack.enchant(ench, ench.getMaxLevel() + 1);
        return true;
    }

    private static void teleportRandom(ServerPlayer player, int range, RandomSource random) {
        ServerLevel level = player.serverLevel();
        // try up to 16 times using LivingEntity.randomTeleport
        for (int i = 0; i < 16; i++) {
            double dx = player.getX() + (random.nextDouble() - 0.5) * range * 2;
            double dy = Mth.clamp(player.getY() + random.nextInt(range) - range / 2.0, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            double dz = player.getZ() + (random.nextDouble() - 0.5) * range * 2;
            if (player.randomTeleport(dx, dy, dz, true)) {
                level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                break;
            }
        }
    }

    private static void summon(ServerPlayer player, String entityId, int count) {
        if (entityId == null || entityId.isEmpty()) return;
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(entityId);
        } catch (Exception e) {
            LOGGER.warn("Invalid entity id {}", entityId);
            return;
        }
        var type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null) {
            LOGGER.warn("Unknown entity {}", entityId);
            return;
        }
        summonEntityType(player, type, count);
    }

    /**
     * 指定 EntityType をプレイヤー前方 3 ブロックに召喚する。
     * テイム可能な動物（オオカミ/ネコ/オウム等）は自動テイム、馬系は乗れるよう懐かせる。
     */
    private static void summonEntityType(ServerPlayer player, EntityType<?> type, int count) {
        ServerLevel level = player.serverLevel();
        var look = player.getLookAngle();
        double sx = player.getX() + look.x * 3;
        double sy = player.getY();
        double sz = player.getZ() + look.z * 3;
        for (int i = 0; i < count; i++) {
            var entity = type.create(level);
            if (entity == null) continue;
            entity.moveTo(sx, sy, sz, player.getYRot(), 0);
            if (entity instanceof TamableAnimal tamable) {
                try {
                    tamable.tame(player);
                } catch (Exception e) {
                    // some versions require setOwnerUUID
                }
                tamable.setOwnerUUID(player.getUUID());
            }
            if (entity instanceof AbstractHorse horse) {
                try {
                    horse.tameWithName(player);
                } catch (Exception e) {
                    // fall through: untamed horse is still a friend
                }
                horse.setOwnerUUID(player.getUUID());
            }
            level.addFreshEntity(entity);
        }
    }

    private static void applyWeather(ServerLevel level, String kind, int duration) {
        if ("clear".equalsIgnoreCase(kind)) {
            level.setWeatherParameters(duration, 0, false, false);
        } else if ("rain".equalsIgnoreCase(kind)) {
            level.setWeatherParameters(0, duration, true, false);
        } else if ("thunder".equalsIgnoreCase(kind)) {
            level.setWeatherParameters(0, duration, true, true);
        }
    }

    private static void punish(ServerPlayer player, String kind) {
        ServerLevel level = player.serverLevel();
        player.displayClientMessage(Component.translatable("bamboomod.wish.greed.line").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        switch (kind.toLowerCase()) {
            case "lightning" -> {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(player.getX(), player.getY(), player.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.0F);
            }
            case "drain_xp" -> player.giveExperienceLevels(-10);
            case "summon_hostile" -> {
                String[] hostiles = {"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"};
                String pick = hostiles[player.getRandom().nextInt(hostiles.length)];
                summon(player, pick, 2);
            }
            case "nothing" -> {
            }
            default -> {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(player.getX(), player.getY(), player.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
    }

    private static void fallback(ServerPlayer player) {
        String mode = WishConfig.COMMON.fallbackMode.get();
        if (mode == null) mode = "random";
        mode = mode.toLowerCase();
        if ("nothing".equals(mode)) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.water").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            return;
        }
        if ("iron".equals(mode)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_ingot"));
            if (item != null) {
                giveItemWithAutoEnchant(player, item, 1, false, player.getRandom(), "bamboomod.wish.fallback.iron");
            }
            return;
        }
        // random
        int r = player.getRandom().nextInt(100);
        if (r < 50) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.water").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (r < 70) {
            player.hurt(player.damageSources().magic(), 2.0F);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.pain").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (r < 90) {
            player.giveExperienceLevels(-1);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.xp").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_ingot"));
            if (item != null) {
                giveItemWithAutoEnchant(player, item, 1, false, player.getRandom(), "bamboomod.wish.fallback.iron");
            }
        }
    }
}
