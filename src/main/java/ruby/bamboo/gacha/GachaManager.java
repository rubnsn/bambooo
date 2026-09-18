package ruby.bamboo.gacha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.GachaDrawResultPacket;

/**
 * ガチャのサーバー権威ロジック。
 * <p>
 * 抽選10連 → pending保持 → Claimで払い出し。2重受取防止のため払い出し時に削除。
 * 切断・リログ時は破棄 (複製防止のため復元しない)。
 */
public final class GachaManager {
    /** レア枠の重み: C 85 / R 12 / SR 3 */
    private static final int W_COMMON = 85;
    private static final int W_RARE = 12;
    private static final int W_SR = 3;

    /** 未受取の抽選結果 (UUID→10件)。払い出し・ログアウトで削除。 */
    private static final Map<UUID, List<ItemStack>> PENDING = new HashMap<>();

    private GachaManager() {
    }

    /** 1回分のレアリティ抽選。 */
    public static GachaRarity rollRarity(RandomSource random) {
        int t = random.nextInt(W_COMMON + W_RARE + W_SR);
        if (t < W_SR) {
            return GachaRarity.SUPER_RARE;
        }
        if (t < W_SR + W_RARE) {
            return GachaRarity.RARE;
        }
        return GachaRarity.COMMON;
    }

    /** 枠内weight抽選。 */
    public static ItemStack rollEntry(GachaRarity rarity, RandomSource random) {
        List<GachaEntry> table = GachaTableLoader.INSTANCE.table(rarity);
        int total = 0;
        for (GachaEntry e : table) {
            total += e.weight;
        }
        if (total <= 0) {
            return new ItemStack(Items.STONE, 8);
        }
        int t = random.nextInt(total);
        for (GachaEntry e : table) {
            t -= e.weight;
            if (t < 0) {
                ItemStack s = e.roll(random);
                if (s != null && !s.isEmpty()) {
                    return s;
                }
                return new ItemStack(Items.STONE, 8);
            }
        }
        return new ItemStack(Items.STONE, 8);
    }

    /** 10連要求の処理。コイン消費→抽選→pending保存→結果送信。 */
    public static void request(ServerPlayer player) {
        // スタブ: 常時無料。Cap化時はここで残高不足を弾く
        if (!CoinWallet.ALWAYS_FREE && !CoinWallet.tryConsume(player, CoinWallet.COST_10)) {
            BambooNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    GachaDrawResultPacket.empty());
            return;
        }
        RandomSource random = player.getRandom();
        List<GachaRarity> rarities = new ArrayList<>(10);
        List<ItemStack> stacks = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            GachaRarity r = rollRarity(random);
            rarities.add(r);
            stacks.add(rollEntry(r, random));
        }
        PENDING.put(player.getUUID(), stacks.stream().map(ItemStack::copy).toList());
        BambooNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new GachaDrawResultPacket(rarities, stacks));
    }

    /** 開封完了後の払い出し。pendingが無ければ何もしない (2重受取防止)。 */
    public static void claim(ServerPlayer player) {
        List<ItemStack> pending = PENDING.remove(player.getUUID());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (ItemStack s : pending) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(s.copy())) {
                player.drop(s.copy(), false);
            }
        }
    }

    public static void onLogout(UUID id) {
        PENDING.remove(id);
    }

    public static void clear() {
        PENDING.clear();
    }
}
