package ruby.bamboo.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.network.SkillSyncPacket;

/**
 * スキル操作の単一入口 (サーバー側)。
 * 変更後は必ず同期パケットを送る。
 *
 * <p>1.21.1 NeoForge: 旧 LazyOptional を捨て、Player Attachment
 * ({@code player.getData(BambooCapabilities.SKILL)}) で直接取得する。
 */
public final class SkillHelper {

    private SkillHelper() {
    }

    public static SkillStorage get(Player player) {
        return player.getData(BambooCapabilities.SKILL);
    }

    public static int getLevel(Player player, SkillType type) {
        return get(player).getLevel(type);
    }

    /** xp 加算。上昇時はメッセージ + 同期。 */
    public static boolean addXp(Player player, SkillType type, int amount) {
        boolean leveled = get(player).addXp(type, amount);
        if (leveled) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.bamboomod.skill.levelup",
                                Component.translatable(
                                        "skill.bamboomod." + type.getId() + ".name"),
                                getLevel(player, type)),
                        false);
            }
            SkillEffects.applyPersistent(player);
            sync(player);
        }
        return leveled;
    }

    /** xp 加算 (小数、0.01精度)。上昇時はメッセージ + 同期。 */
    public static boolean addXpDouble(Player player, SkillType type, double amount) {
        boolean leveled = get(player).addXpDouble(type, amount);
        if (leveled) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.bamboomod.skill.levelup",
                                Component.translatable(
                                        "skill.bamboomod." + type.getId() + ".name"),
                                getLevel(player, type)),
                        false);
            }
            SkillEffects.applyPersistent(player);
            sync(player);
        }
        return leveled;
    }

    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        PacketDistributor.sendToPlayer(sp, new SkillSyncPacket(get(player).serializeNBT()));
    }

    public static void syncTo(ServerPlayer sp) {
        sync(sp);
    }
}
