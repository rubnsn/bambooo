package ruby.bamboo.skill;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;
import ruby.bamboo.core.init.BambooCapabilities;

/**
 * スキル永続・同期・デバッグコマンド (feat-spec-skill Phase0)。
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

    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, builder) -> {
        for (SkillType t : SkillType.values()) {
            builder.suggest(t.getId());
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bambooskill")
                .then(Commands.literal("view").executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    StringBuilder sb = new StringBuilder();
                    SkillHelper.get(sp).ifPresent(s -> {
                        for (SkillType t : SkillType.values()) {
                            sb.append(t.getId()).append(" Lv").append(s.getLevel(t))
                                    .append(" ").append(s.getXp(t)).append("/").append(s.getNext(t)).append(" ");
                        }
                    });
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("addxp")
                        .requires(src -> src.hasPermission(2))
                        .then(net.minecraft.commands.Commands.argument("skill", StringArgumentType.word())
                                .suggests(SKILL_IDS)
                                .then(net.minecraft.commands.Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            SkillType type = SkillType
                                                    .byId(StringArgumentType.getString(ctx, "skill"));
                                            if (type == null) {
                                                ctx.getSource().sendFailure(Component.literal("unknown skill"));
                                                return 0;
                                            }
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                            boolean up = SkillHelper.addXp(sp, type, amount);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(type.getId() + " +xp" + amount + (up ? " LEVELUP" : "")),
                                                    false);
                                            return 1;
                                        })))));
    }
}
