package ruby.bamboo.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * スキル永続・同期 (feat-spec-skill Phase0)。
 * 死亡時は維持 (wasDeath によらず全コピー)。
 */
@Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillEvents {

    private SkillEvents() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(BambooCapabilities.SKILL).ifPresent(old -> {
            event.getEntity().getCapability(BambooCapabilities.SKILL).ifPresent(nu -> {
                nu.deserializeNBT(old.serializeNBT());
            });
        });
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SkillEffects.applyPersistent(sp);
            SkillHelper.syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SkillEffects.applyPersistent(sp);
            SkillHelper.syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SkillEffects.applyPersistent(sp);
            SkillHelper.syncTo(sp);
        }
    }
}
