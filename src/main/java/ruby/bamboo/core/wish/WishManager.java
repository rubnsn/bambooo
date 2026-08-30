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
        // 願い叫びを全員にブロードキャスト（システムメッセージ風）
        String shoutRaw = rawInput == null ? "" : rawInput.trim().replaceAll("\\p{Cntrl}", "");
        if (shoutRaw.length() > 30) shoutRaw = shoutRaw.substring(0, 30);
        if (!shoutRaw.isEmpty()) {
            Component shout = Component.translatable("bamboomod.wish.shout", player.getName().getString(), shoutRaw)
                    .withStyle(ChatFormatting.YELLOW);
            if (player.server != null) {
                player.server.getPlayerList().broadcastSystemMessage(shout, false);
            } else {
                player.displayClientMessage(shout, false);
            }
        }
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

        // 2.5 approximate wish entry search (1回、最も近似値の願いを再検索)
        WishEntry approx = findClosestApproximate(normalized, random);
        if (approx != null) {
            LOGGER.info("Wish approximate matched {} for '{}' (distance minimal)", approx.id, normalized);
            executeEntry(player, approx, random);
            return;
        }

        // 3. entity name direct summon (動物/モンスター名をそのまま入力)
        EntityType<?> hitType = WishEntitySearch.find(normalized, level);
        if (hitType != null) {
            summonEntityType(player, hitType, 1);
            Component name = hitType.getDescription();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.summon", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
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
                boolean over = giveItemInternal(player, item, 1, null, random);
                if (over) {
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
                } else {
                    ItemStack tmp = new ItemStack(item);
                    Component name = tmp.getHoverName();
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                }
                return;
            }
            // 近似も含めてアイテムが見つからなかった場合はエントリの近似再検索を試みず即フォールバック（アイテム語が完全に外れている）
            fallback(player);
            return;
        }

        // 4.5 vague category random (「武器！」「アイテム！」など不正確な願いはカテゴリからランダム)
        if (handleCategoryRandom(player, normalized, random)) {
            return;
        }

        // 4.6 loose random char match (完全マッチしなかったら正確性を捨ててランダムな1文字が一致したらその願いを叶える)
        if (tryRandomCharWish(player, normalized, random)) {
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

    /**
     * 最も近似値の願いを1回再検索する。特定条件に一切マッチしなかった場合のフォールバック前に呼び出される。
     * 各エントリの pattern を '|' で分割し、正規化後の各トークンと入力のレーベンシュタイン距離を計算。
     * 最小距離が閾値以下のエントリのうち最小距離のものを、重み付きランダムで1件返す。
     * punishment エントリは近似対象から除外する。
     */
    private static WishEntry findClosestApproximate(String normalizedInput, RandomSource random) {
        if (normalizedInput == null || normalizedInput.isEmpty()) return null;
        List<WishEntry> candidates;
        synchronized (WishManager.class) {
            candidates = new ArrayList<>(ENTRIES);
        }
        int len = normalizedInput.length();
        int threshold;
        if (len <= 3) threshold = 1;
        else if (len <= 6) threshold = 2;
        else threshold = 3;

        int bestDist = Integer.MAX_VALUE;
        List<WishEntry> bestEntries = new ArrayList<>();
        for (WishEntry e : candidates) {
            if (isPunishment(e)) continue;
            String pattern = e.pattern;
            if (pattern == null || pattern.isEmpty()) continue;
            String cleaned = pattern.replaceAll("[\\^\\$\\(\\)\\[\\]]", "");
            String[] tokens = cleaned.split("\\|");
            int entryBest = Integer.MAX_VALUE;
            for (String tok : tokens) {
                if (tok == null) continue;
                tok = tok.trim();
                if (tok.isEmpty()) continue;
                String normTok = WishNormalizer.normalize(tok);
                if (normTok.isEmpty()) continue;
                int dist = levenshtein(normalizedInput, normTok);
                if (dist < entryBest) entryBest = dist;
                // also consider substring containment as distance 0? but exact contains already checked, so not needed
            }
            if (entryBest == Integer.MAX_VALUE) continue;
            if (entryBest < bestDist) {
                bestDist = entryBest;
                bestEntries.clear();
                bestEntries.add(e);
            } else if (entryBest == bestDist) {
                bestEntries.add(e);
            }
        }
        if (bestEntries.isEmpty() || bestDist > threshold) return null;
        return weightedRandom(bestEntries, random);
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    private static boolean handleCategoryRandom(ServerPlayer player, String normalized, RandomSource random) {
        // 正規化済み入力はひらがな/小文字化済み。句読点は除去せず contains で判定するため、lang基準の正確な名前を要求する意図に沿う
        // ただし末尾の「！」「!」等は無視してカテゴリ判定する
        String stripped = normalized.replaceAll("[\\p{Punct}！。、]+$", "").trim();
        stripped = stripped.replaceAll("^[\\p{Punct}！。、]+", "").trim();
        if (stripped.isEmpty()) return false;

        // サブカテゴリを優先（「剣」「斧」等が含まれていればトップカテゴリより細分化）
        // 剣: けん/剣/そーど/sword
        if (containsAny(normalized, "けん", "剣", "そーど", "sword")) {
            // 武器 剣 のようにトップカテゴリと併記でもここで拾われる
            giveRandomSword(player, random);
            return true;
        }
        if (containsAny(normalized, "おの", "斧", "あっくす", "axe")) {
            giveRandomAxe(player, random);
            return true;
        }
        if (containsAny(normalized, "つるはし", "ぴっける", "pickaxe", "pick")) {
            giveRandomPickaxe(player, random);
            return true;
        }
        if (containsAny(normalized, "しゃべる", "シャベル", "shovel")) {
            // WishNormalizerで シャベル→しゃべる に変換されるため両方カバー
            giveRandomShovel(player, random);
            return true;
        }
        if (containsAny(normalized, "くわ", "鍬", "hoe")) {
            giveRandomHoe(player, random);
            return true;
        }
        if (containsAny(normalized, "ゆみ", "弓", "bow") && !containsAny(normalized, "くろすぼう", "crossbow")) {
            // 弓はクロスボウと区別
            giveRandomBow(player, random);
            return true;
        }
        if (containsAny(normalized, "くろすぼう", "crossbow")) {
            giveRandomCrossbow(player, random);
            return true;
        }
        if (containsAny(normalized, "とらいでんと", "trident")) {
            giveRandomTrident(player, random);
            return true;
        }
        if (containsAny(normalized, "つりざお", "釣り竿", "つり", "fishing", "rod")) {
            giveRandomFishingRod(player, random);
            return true;
        }
        // 防具部位: 防具 頭 のようにトップ + 部位で指定、部位単体でも可
        if (containsAny(normalized, "へるめっと", "ヘルメット", "かぶと", "兜", "あたま", "頭", "helmet", "helm")) {
            // 「防具 頭」でも「頭」単体でもヘルメット
            giveRandomHelmet(player, random);
            return true;
        }
        if (containsAny(normalized, "ちぇすとぷれーと", "チェストプレート", "むね", "胸", "chestplate", "chest")) {
            giveRandomChestplate(player, random);
            return true;
        }
        if (containsAny(normalized, "れぎんす", "レギンス", "leggings", "れっぎんす")) {
            giveRandomLeggings(player, random);
            return true;
        }
        if (containsAny(normalized, "ぶーつ", "ブーツ", "boots", "くつ", "靴")) {
            giveRandomBoots(player, random);
            return true;
        }
        if (containsAny(normalized, "たて", "盾", "shield")) {
            giveRandomShield(player, random);
            return true;
        }
        if (containsAny(normalized, "しょもつ", "書物", "えんちゃんと", "エンチャント", "ほん", "本", "ぶっく", "book")) {
            // 「道具 書物」や「本」単体はエンチャント本、ただし「本」が単独のときのみ厳密に（1文字のため誤爆防止で長さチェック）
            String tmpStripped = stripped;
            boolean isBookAlone = tmpStripped.equals("ほん") || tmpStripped.equals("本") || tmpStripped.equals("ぶっく") || tmpStripped.equals("book");
            boolean isBookCompound = containsAny(normalized, "しょもつ", "書物", "えんちゃんと");
            if (isBookAlone || isBookCompound || (containsAny(normalized, "ほん", "本") && stripped.length() <= 3)) {
                giveRandomEnchantedBook(player, random);
                return true;
            }
        }
        // トップカテゴリ（部位指定なし）
        if (stripped.equals("ぶき") || stripped.equals("武器") || stripped.equals("weapon")) {
            giveRandomWeapon(player, random);
            return true;
        }
        if (stripped.equals("あいてむ") || stripped.equals("item")) {
            giveRandomItem(player, random);
            return true;
        }
        if (containsAny(stripped, "ぼうぐ", "防具", "armor", "armour")) {
            // 部位なしの「防具！」は全防具からランダム、部位ありは上で既に処理済み
            if (!containsAny(normalized, "あたま", "頭", "へるめっと", "helmet", "むね", "胸", "ちぇすと", "れぎんす", "ぶーつ", "たて", "盾")) {
                giveRandomArmor(player, random);
                return true;
            }
        }
        if (stripped.equals("どうぐ") || stripped.equals("道具") || stripped.equals("tool")) {
            giveRandomTool(player, random);
            return true;
        }
        if (containsAny(stripped, "たべもの", "食べ物", "food", "しょくひん", "食品")) {
            giveRandomFood(player, random);
            return true;
        }
        return false;
    }

    private static boolean containsAny(String s, String... keywords) {
        for (String kw : keywords) {
            String normKw = WishNormalizer.normalize(kw);
            if (!normKw.isEmpty() && s.contains(normKw)) return true;
            // raw kwも直接チェック（漢字など正規化で変わらない場合）
            if (s.contains(kw)) return true;
        }
        return false;
    }

    private static boolean tryRandomCharWish(ServerPlayer player, String normalized, RandomSource random) {
        if (normalized == null || normalized.isEmpty()) return false;
        String stripped = normalized.replaceAll("\\s+", "");
        if (stripped.isEmpty()) return false;
        // ランダムに1文字選ぶ
        int idx = random.nextInt(stripped.length());
        char c = stripped.charAt(idx);
        String charStr = String.valueOf(c);
        String normChar = WishNormalizer.normalize(charStr);
        if (normChar.isEmpty()) normChar = charStr;
        // 記号や空白はスキップ（次の文字を試す）
        if (normChar.trim().isEmpty() || normChar.matches("[\\p{Punct}！。、]+")) {
            return false;
        }
        List<WishEntry> candidates = new ArrayList<>();
        synchronized (WishManager.class) {
            for (WishEntry e : ENTRIES) {
                if (isPunishment(e)) continue;
                String pattern = e.pattern;
                if (pattern == null || pattern.isEmpty()) continue;
                String cleaned = pattern.replaceAll("[\\^\\$\\(\\)\\[\\]]", "");
                String[] tokens = cleaned.split("\\|");
                for (String tok : tokens) {
                    String normTok = WishNormalizer.normalize(tok);
                    if (normTok.isEmpty()) continue;
                    if (normTok.contains(normChar) || normTok.contains(charStr)) {
                        candidates.add(e);
                        break;
                    }
                    // トークン側の1文字が入力文字と一致でも可（逆方向）
                    if (charStr.length() == 1 && normTok.indexOf(charStr.charAt(0)) >= 0) {
                        candidates.add(e);
                        break;
                    }
                }
            }
        }
        if (candidates.isEmpty()) return false;
        WishEntry chosen = candidates.get(random.nextInt(candidates.size()));
        LOGGER.info("Wish random char '{}' (from '{}') matched {} (pattern '{}')", charStr, normalized, chosen.id, chosen.pattern);
        executeEntry(player, chosen, random);
        return true;
    }

    private static void giveRandomWeapon(ServerPlayer player, RandomSource random) {
        String[] weapons = {
            "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword", "minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:golden_sword",
            "minecraft:bow", "minecraft:crossbow", "minecraft:trident",
            "bamboomod:commonkatana", "bamboomod:bamboobow", "minecraft:wooden_axe", "minecraft:iron_axe", "minecraft:diamond_axe"
        };
        String pick = weapons[random.nextInt(weapons.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_sword"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            ItemStack tmp = new ItemStack(item);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void giveRandomArmor(ServerPlayer player, RandomSource random) {
        String[] armors = {
            "minecraft:leather_helmet", "minecraft:chainmail_helmet", "minecraft:iron_helmet", "minecraft:diamond_helmet", "minecraft:netherite_helmet",
            "minecraft:iron_chestplate", "minecraft:diamond_chestplate", "minecraft:netherite_chestplate",
            "minecraft:iron_leggings", "minecraft:diamond_leggings",
            "minecraft:iron_boots", "minecraft:diamond_boots"
        };
        String pick = armors[random.nextInt(armors.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_chestplate"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            ItemStack tmp = new ItemStack(item);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void giveRandomTool(ServerPlayer player, RandomSource random) {
        String[] tools = {
            "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe",
            "minecraft:iron_shovel", "minecraft:diamond_shovel",
            "minecraft:iron_axe", "minecraft:diamond_axe",
            "minecraft:iron_hoe", "minecraft:diamond_hoe",
            "bamboomod:paddy_field_hoe"
        };
        String pick = tools[random.nextInt(tools.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_pickaxe"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            ItemStack tmp = new ItemStack(item);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void giveRandomSword(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:wooden_sword","minecraft:stone_sword","minecraft:iron_sword","minecraft:diamond_sword","minecraft:netherite_sword","minecraft:golden_sword","bamboomod:commonkatana"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_sword"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomAxe(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:wooden_axe","minecraft:stone_axe","minecraft:iron_axe","minecraft:diamond_axe","minecraft:netherite_axe","minecraft:golden_axe"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_axe"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomPickaxe(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:wooden_pickaxe","minecraft:stone_pickaxe","minecraft:iron_pickaxe","minecraft:diamond_pickaxe","minecraft:netherite_pickaxe","minecraft:golden_pickaxe"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_pickaxe"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomShovel(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:wooden_shovel","minecraft:stone_shovel","minecraft:iron_shovel","minecraft:diamond_shovel","minecraft:netherite_shovel","minecraft:golden_shovel"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_shovel"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomHoe(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:wooden_hoe","minecraft:stone_hoe","minecraft:iron_hoe","minecraft:diamond_hoe","minecraft:netherite_hoe","minecraft:golden_hoe","bamboomod:paddy_field_hoe"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_hoe"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomBow(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:bow","bamboomod:bamboobow"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:bow"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomCrossbow(ServerPlayer player, RandomSource random) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:crossbow"));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:bow"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomTrident(ServerPlayer player, RandomSource random) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:trident"));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_sword"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomFishingRod(ServerPlayer player, RandomSource random) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:fishing_rod"));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stick"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomHelmet(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:leather_helmet","minecraft:chainmail_helmet","minecraft:iron_helmet","minecraft:diamond_helmet","minecraft:netherite_helmet","minecraft:golden_helmet","minecraft:turtle_helmet"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_helmet"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomChestplate(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:leather_chestplate","minecraft:chainmail_chestplate","minecraft:iron_chestplate","minecraft:diamond_chestplate","minecraft:netherite_chestplate","minecraft:golden_chestplate","minecraft:elytra"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_chestplate"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomLeggings(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:leather_leggings","minecraft:chainmail_leggings","minecraft:iron_leggings","minecraft:diamond_leggings","minecraft:netherite_leggings","minecraft:golden_leggings"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_leggings"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomBoots(ServerPlayer player, RandomSource random) {
        String[] list = {"minecraft:leather_boots","minecraft:chainmail_boots","minecraft:iron_boots","minecraft:diamond_boots","minecraft:netherite_boots","minecraft:golden_boots"};
        String pick = list[random.nextInt(list.length)];
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(pick));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_boots"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomShield(ServerPlayer player, RandomSource random) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:shield"));
        if (item == null) item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:iron_ingot"));
        boolean over = giveItemInternal(player, item, 1, null, random);
        if (over) player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        else { ItemStack tmp=new ItemStack(item); Component name=tmp.getHoverName(); player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false); }
    }

    private static void giveRandomEnchantedBook(ServerPlayer player, RandomSource random) {
        // ランダムなエンチャントを1つ選び、エンチャント本として付与
        var enchantments = ForgeRegistries.ENCHANTMENTS.getValues().stream().filter(e -> e != null && e.isDiscoverable() && !e.isCurse()).toList();
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        if (!enchantments.isEmpty()) {
            var ench = enchantments.get(random.nextInt(enchantments.size()));
            int lvl = 1 + random.nextInt(ench.getMaxLevel());
            // EnchantedBookItem は EnchantmentInstance を使う
            net.minecraft.world.item.EnchantedBookItem.addEnchantment(book, new net.minecraft.world.item.enchantment.EnchantmentInstance(ench, lvl));
        }
        // 頭上にスポーン
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 10;
        double z = player.getZ();
        ItemEntity entity = new ItemEntity(level, x, y, z, book);
        entity.setDeltaMovement(0, 0, 0);
        entity.setPickUpDelay(10);
        level.addFreshEntity(entity);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        ItemStack tmp = book;
        Component name = tmp.getHoverName();
        player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
    }

    private static void giveRandomFood(ServerPlayer player, RandomSource random) {
        // 優先は bamboomod の竹食料、なければバニラ
        List<Item> candidates = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            ItemStack s = new ItemStack(it);
            if (s.isEmpty()) continue;
            if (s.getFoodProperties(null) != null) {
                candidates.add(it);
            }
        }
        Item pick;
        if (!candidates.isEmpty()) {
            pick = candidates.get(random.nextInt(candidates.size()));
        } else {
            pick = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:cooked_beef"));
        }
        boolean over = giveItemInternal(player, pick, 3 + random.nextInt(3), false, random);
        // food は overenchant しないが、一応分岐
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            ItemStack tmp = new ItemStack(pick);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void giveRandomItem(ServerPlayer player, RandomSource random) {
        List<Item> all = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            ItemStack s = new ItemStack(it);
            if (s.isEmpty()) continue;
            // AIR etc除外、stackが空でないもの
            all.add(it);
        }
        Item pick;
        if (!all.isEmpty()) {
            pick = all.get(random.nextInt(all.size()));
        } else {
            pick = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:stone"));
        }
        boolean over = giveItemInternal(player, pick, 1, null, random);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            ItemStack tmp = new ItemStack(pick);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void executeEntry(ServerPlayer player, WishEntry entry, RandomSource random) {
        LOGGER.info("Wish matched {} for {}: '{}' -> {}", entry.id, player.getName().getString(), entry.pattern, entry.effects.size());
        boolean hasOver = false;
        boolean hasPunishment = false;
        boolean hasKill = false;
        String punishmentKind = "lightning";
        Item firstGiveItem = null;
        String firstSummonId = null;
        String firstWeatherKind = null;
        String firstEffectType = null;
        for (WishEffect eff : entry.effects) {
            if (firstEffectType == null) firstEffectType = eff.type;
            if ("punishment".equalsIgnoreCase(eff.type)) {
                hasPunishment = true;
                if (eff.args.has("kind")) {
                    punishmentKind = eff.args.get("kind").getAsString();
                }
                punishInternal(player, punishmentKind);
            } else if ("kill".equalsIgnoreCase(eff.type)) {
                hasKill = true;
                killInternal(player);
            } else if ("give_item".equalsIgnoreCase(eff.type)) {
                String itemStr = eff.args.has("item") ? eff.args.get("item").getAsString() : "";
                int count = eff.args.has("count") ? eff.args.get("count").getAsInt() : 1;
                Boolean enchanted = null;
                if (eff.args.has("enchanted")) enchanted = eff.args.get("enchanted").getAsBoolean();
                ResourceLocation rl = new ResourceLocation(itemStr);
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) {
                    LOGGER.warn("Unknown item {}", itemStr);
                    continue;
                }
                if (firstGiveItem == null) firstGiveItem = item;
                boolean over = giveItemInternal(player, item, count, enchanted, random);
                if (over) hasOver = true;
            } else if ("summon".equalsIgnoreCase(eff.type)) {
                String entityStr = eff.args.has("entity") ? eff.args.get("entity").getAsString() : "";
                if (firstSummonId == null) firstSummonId = entityStr;
                executeEffectInternal(player, eff, random);
            } else if ("weather".equalsIgnoreCase(eff.type)) {
                String kind = eff.args.has("kind") ? eff.args.get("kind").getAsString() : "clear";
                if (firstWeatherKind == null) firstWeatherKind = kind;
                executeEffectInternal(player, eff, random);
            } else {
                executeEffectInternal(player, eff, random);
            }
        }
        if (hasKill) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.death").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
        } else if (hasPunishment) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.greed.line").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (hasOver) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else if (firstGiveItem != null) {
            ItemStack tmp = new ItemStack(firstGiveItem);
            Component name = tmp.getHoverName();
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.item", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (firstSummonId != null) {
            Component name = getEntityDisplayName(firstSummonId);
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.summon", name).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if ("xp".equalsIgnoreCase(firstEffectType)) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.xp").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if ("heal".equalsIgnoreCase(firstEffectType)) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.heal").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if ("effect_potion".equalsIgnoreCase(firstEffectType)) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.potion").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if ("teleport_random".equalsIgnoreCase(firstEffectType)) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.teleport").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if ("weather".equalsIgnoreCase(firstEffectType) && firstWeatherKind != null) {
            String key = "bamboomod.wish.result.weather." + firstWeatherKind.toLowerCase();
            // fallback to generic weather if unknown kind
            if (!firstWeatherKind.equalsIgnoreCase("clear") && !firstWeatherKind.equalsIgnoreCase("rain") && !firstWeatherKind.equalsIgnoreCase("thunder")) {
                key = "bamboomod.wish.result.generic";
            }
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (entry.message != null && !entry.message.isEmpty()) {
            player.displayClientMessage(Component.translatable(entry.message).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static Component getEntityDisplayName(String entityId) {
        try {
            ResourceLocation rl = new ResourceLocation(entityId);
            var type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type != null) {
                return type.getDescription();
            }
        } catch (Exception ignored) {
        }
        return Component.literal(entityId);
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
                case "kill" -> {
                    killInternal(player);
                    player.displayClientMessage(Component.translatable("bamboomod.wish.result.death").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
                }
                default -> LOGGER.warn("Unknown wish effect type {}", type);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to execute wish effect {}", type, ex);
        }
    }

    private static void giveItemWithAutoEnchant(ServerPlayer player, Item item, int count, Boolean enchantedFlag, RandomSource random, String messageKey) {
        boolean over = giveItemInternal(player, item, count, enchantedFlag, random);
        if (over) {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.overenchant").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
        } else if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            player.displayClientMessage(Component.translatable("bamboomod.wish.result.generic").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    /**
     * アイテムを頭上にスポーンさせる内部処理。チャットは送らない。
     * 道具類（TieredItem/Bow/Crossbow/Trident/FishingRod/Armor/Shield）は1個目のエンチャントを必ずオーバーエンチャント、2個目以降は低確率を維持。エンチャント本は対象外。
     * @return overenchant が発生したら true
     */
    private static boolean giveItemInternal(ServerPlayer player, Item item, int count, Boolean enchantedFlag, RandomSource random) {
        ItemStack stack = new ItemStack(item, count);
        boolean enchanted = enchantedFlag != null ? enchantedFlag : stack.getMaxDamage() > 0;
        boolean over = false;
        if (enchanted && stack.isEnchantable()) {
            stack = EnchantmentHelper.enchantItem(random, stack, 30, true);
            int overChance = WishConfig.COMMON.overenchantChance.get();
            // エンチャント本は対象外（EnchantedBookItemは isEnchantable==false なのでここには来ないが念のため）
            boolean isToolCategory = isToolForGuaranteedOverenchant(item);
            if (isToolCategory) {
                // 道具類は1個必ず、それ以降は低確率
                if (tryOverEnchant(stack, random)) {
                    over = true;
                    // 2個目のエンチャントがあれば低確率でさらにオーバー
                    if (overChance > 0 && random.nextInt(overChance) == 0) {
                        // 別エンチャントを試す（1個しかない場合はスキップ）
                        if (EnchantmentHelper.getEnchantments(stack).size() > 1) {
                            // 1個目は既にオーバー済みなので、2個目は別エンチャントを狙う
                            // tryOverEnchantはランダムな1件を選ぶため、既にオーバーしたものと被る可能性はあるが低確率のため許容
                            // より厳密に別を狙うなら tryOverEnchantDistinct を使う
                            boolean second = tryOverEnchantDistinct(stack, random);
                            if (second) over = true;
                        }
                    }
                }
            } else {
                if (overChance > 0 && random.nextInt(overChance) == 0) {
                    over = tryOverEnchant(stack, random);
                }
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
        return over;
    }

    private static boolean isToolForGuaranteedOverenchant(Item item) {
        // エンチャント本は除外
        if (item instanceof net.minecraft.world.item.EnchantedBookItem) return false;
        return item instanceof net.minecraft.world.item.TieredItem
                || item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.TridentItem
                || item instanceof net.minecraft.world.item.FishingRodItem
                || item instanceof net.minecraft.world.item.ArmorItem
                || item instanceof net.minecraft.world.item.ShieldItem;
    }

    private static boolean tryOverEnchantDistinct(ItemStack stack, RandomSource random) {
        java.util.Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        if (map.size() <= 1) return false;
        // 既に最大+1 になっているエンチャントを除外して別を狙う（簡易: 最大レベルを超えているものは除外）
        List<Enchantment> candidates = new ArrayList<>();
        for (var e : map.entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = e.getValue();
            if (lvl <= ench.getMaxLevel()) {
                candidates.add(ench);
            }
        }
        if (candidates.isEmpty()) return false;
        Enchantment ench = candidates.get(random.nextInt(candidates.size()));
        stack.enchant(ench, ench.getMaxLevel() + 1);
        return true;
    }

    private static void executeEffectInternal(ServerPlayer player, WishEffect eff, RandomSource random) {
        ServerLevel level = player.serverLevel();
        String type = eff.type;
        try {
            switch (type) {
                case "xp" -> {
                    int levels = eff.args.has("levels") ? eff.args.get("levels").getAsInt() : 30;
                    player.giveExperienceLevels(levels);
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
                }
                case "teleport_random" -> {
                    int range = eff.args.has("range") ? eff.args.get("range").getAsInt() : 64;
                    teleportRandom(player, range, random);
                }
                case "summon" -> {
                    String entityStr = eff.args.has("entity") ? eff.args.get("entity").getAsString() : "";
                    int cnt = eff.args.has("count") ? eff.args.get("count").getAsInt() : 1;
                    summon(player, entityStr, cnt);
                }
                case "weather" -> {
                    String kind = eff.args.has("kind") ? eff.args.get("kind").getAsString() : "clear";
                    int dur = eff.args.has("duration") ? eff.args.get("duration").getAsInt() : 6000;
                    applyWeather(level, kind, dur);
                }
                case "heal" -> {
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().eat(20, 20);
                }
                case "kill" -> {
                    killInternal(player);
                }
                default -> LOGGER.warn("Unknown wish effect type {} (internal)", type);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to execute wish effect (internal) {}", type, ex);
        }
    }

    private static void killInternal(ServerPlayer player) {
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        if (!player.isDeadOrDying()) {
            player.setHealth(0.0F);
            player.die(player.damageSources().genericKill());
        }
    }

    private static void punishInternal(ServerPlayer player, String kind) {
        ServerLevel level = player.serverLevel();
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
            placeWaterAtFeet(player);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.water").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            return;
        }
        if ("iron".equals(mode)) {
            spawnFallingDamagedAnvil(player);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.iron").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
            return;
        }
        // random
        int r = player.getRandom().nextInt(100);
        if (r < 50) {
            placeWaterAtFeet(player);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.water").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (r < 70) {
            player.hurt(player.damageSources().magic(), 2.0F);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.pain").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else if (r < 90) {
            player.giveExperienceLevels(-1);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.xp").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        } else {
            spawnFallingDamagedAnvil(player);
            player.displayClientMessage(Component.translatable("bamboomod.wish.fallback.iron").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
        }
    }

    private static void placeWaterAtFeet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var pos = player.blockPosition();
        // 足元が置換可能なら水源を設置、埋まっている場合は1ブロック上に
        if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) {
            pos = pos.above();
        }
        // 水中でなければ設置
        if (level.getBlockState(pos).canBeReplaced() || level.getBlockState(pos).isAir()) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
        } else {
            // 置けない場合は頭上に水バケツ的に水流を
            var above = player.blockPosition().above(2);
            if (level.getBlockState(above).canBeReplaced()) {
                level.setBlock(above, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            }
        }
    }

    private static void spawnFallingDamagedAnvil(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var pos = player.blockPosition().above(10);
        // 上空10ブロックから壊れかけの金床を落下
        var state = net.minecraft.world.level.block.Blocks.DAMAGED_ANVIL.defaultBlockState();
        // 可能なら CHIPPED/DAMAGED のいずれかランダムで壊れかけ感を出す
        if (player.getRandom().nextBoolean()) {
            state = net.minecraft.world.level.block.Blocks.CHIPPED_ANVIL.defaultBlockState();
        }
        var falling = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, pos, state);
        falling.setHurtsEntities(2.0F, 40);
        level.addFreshEntity(falling);
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
    }
}
