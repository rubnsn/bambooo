package ruby.bamboo.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LazyOptional;
import ruby.bamboo.core.init.BambooCapabilities;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.SkillSyncPacket;

/**
 * スキル操作の単一入口 (サーバー側)。
 * 変更後は必ず同期パケットを送る。
 */
public final class SkillHelper {

    private SkillHelper() {
    }

    public static LazyOptional<SkillStorage> get(Player player) {
        return player.getCapability(BambooCapabilities.SKILL);
    }

    public static int getLevel(Player player, SkillType type) {
        return get(player).map(s -> s.getLevel(type)).orElse(0);
    }

    /** xp 加算。上昇時はメッセージ + 同期。 */
    public static boolean addXp(Player player, SkillType type, int amount) {
        boolean[] leveled = { false };
        get(player).ifPresent(s -> {
            if (s.addXp(type, amount)) {
                leveled[0] = true;
            }
        });
        if (leveled[0]) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.bamboomod.skill.levelup",
                                net.minecraft.network.chat.Component.translatable(
                                        "skill.bamboomod." + type.getId() + ".name"),
                                getLevel(player, type)),
                        false);
            }
            SkillEffects.applyPersistent(player);
            sync(player);
        }
        return leveled[0];
    }

    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        get(player).ifPresent(s -> BambooNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                new SkillSyncPacket(s.serializeNBT())));
    }

    public static void syncTo(ServerPlayer sp) {
        sync(sp);
    }
}
