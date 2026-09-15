package ruby.bamboo.skill;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ruby.bamboo.BambooMod;

/**
 * スキル永続・同期 (feat-spec-skill Phase0)。
 *
 * <p>1.21.1 NeoForge: 死亡時維持は Attachment の {@code copyOnDeath()} が担うため
 * 旧 Clone コピーは不要 (削除)。ログイン・次元移動・リスポーン時の
 * 属性再適用 + 全量同期のみ残す。
 */
@EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SkillEvents {

    private SkillEvents() {
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
