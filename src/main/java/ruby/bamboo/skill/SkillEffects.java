package ruby.bamboo.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import ruby.bamboo.BambooMod;

/**
 * 属性系スキルの適用 (feat-spec-skill §3)。
 * 速度: MOVEMENT_SPEED +1%/Lv、泳速: SWIM_SPEED +3%/Lv (要 EntityType 登録)、
 * 盾: 構え中のみ +5%/Lv (減速デバフの相対軽減)。
 *
 * <p>1.21.1 NeoForge: 属性は {@code Holder<Attribute>} 化
 * ({@code Attributes.MOVEMENT_SPEED} / {@code NeoForgeMod.SWIM_SPEED})、
 * Modifier ID は UUID から ResourceLocation 化。
 * {@code player.getAttribute(Holder)} / {@code getModifier(ResourceLocation)} /
 * {@code removeModifier(ResourceLocation)} を使用する。
 */
public final class SkillEffects {

    public static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "speed_skill");
    public static final ResourceLocation SWIM_ID =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "swim_skill");
    public static final ResourceLocation SHIELD_ID =
            ResourceLocation.fromNamespaceAndPath(BambooMod.MODID, "shield_skill");

    private SkillEffects() {
    }

    @EventBusSubscriber(modid = BambooMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onAttributeModify(EntityAttributeModificationEvent event) {
            if (!event.has(EntityType.PLAYER, NeoForgeMod.SWIM_SPEED)) {
                event.add(EntityType.PLAYER, NeoForgeMod.SWIM_SPEED);
            }
        }
    }

    public static void refreshModifier(Player player, Holder<Attribute> attr, ResourceLocation id, double amount,
            AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(id);
        if (amount <= 0.0D) {
            if (existing != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (existing == null || Double.compare(existing.amount(), amount) != 0
                || existing.operation() != op) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /** 常時系 (速度・泳速) を掛け直す。ログイン・転送・リスポーン・上昇時に呼ぶ。 */
    public static void applyPersistent(Player player) {
        int speedLv = SkillHelper.getLevel(player, SkillType.SPEED);
        refreshModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                0.01D * speedLv, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        int swimLv = SkillHelper.getLevel(player, SkillType.SWIM);
        refreshModifier(player, NeoForgeMod.SWIM_SPEED, SWIM_ID,
                0.03D * swimLv, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 盾構え中のみ呼ぶ。非構え時は除去する。 */
    public static void applyShield(Player player, boolean blocking) {
        if (!blocking) {
            refreshModifier(player, Attributes.MOVEMENT_SPEED, SHIELD_ID,
                    0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.SHIELD);
        refreshModifier(player, Attributes.MOVEMENT_SPEED, SHIELD_ID,
                0.05D * lv, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
