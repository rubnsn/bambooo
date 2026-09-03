package ruby.bamboo.skill;

import java.util.UUID;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ruby.bamboo.BambooMod;

/**
 * 属性系スキルの適用 (feat-spec-skill §3)。
 * 速度: MOVEMENT_SPEED +1%/Lv、泳速: SWIM_SPEED +3%/Lv (要 EntityType 登録)、
 * 盾: 構え中のみ +5%/Lv (減速デバフの相対軽減)。
 */
public final class SkillEffects {

    public static final UUID SPEED_UUID = UUID.fromString("3d7f0e1a-7b2c-4a1b-8000-000000535044");
    public static final UUID SWIM_UUID = UUID.fromString("7a21f4c9-1d3e-4c2a-9b5e-5357494d3031");
    public static final UUID SHIELD_UUID = UUID.fromString("9c44aa07-5b11-4d8e-8f2a-534849454c44");

    private SkillEffects() {
    }

    @Mod.EventBusSubscriber(modid = BambooMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onAttributeModify(EntityAttributeModificationEvent event) {
            if (!event.has(EntityType.PLAYER, ForgeMod.SWIM_SPEED.get())) {
                event.add(EntityType.PLAYER, ForgeMod.SWIM_SPEED.get());
            }
        }
    }

    public static void refreshModifier(Player player, Attribute attr, UUID uuid, String name, double amount,
            AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(uuid);
        if (amount <= 0.0D) {
            if (existing != null) {
                inst.removeModifier(uuid);
            }
            return;
        }
        if (existing == null || Double.compare(existing.getAmount(), amount) != 0
                || existing.getOperation() != op) {
            inst.removeModifier(uuid);
            inst.addTransientModifier(new AttributeModifier(uuid, name, amount, op));
        }
    }

    /** 常時系 (速度・泳速) を掛け直す。ログイン・転送・リスポーン・上昇時に呼ぶ。 */
    public static void applyPersistent(Player player) {
        int speedLv = SkillHelper.getLevel(player, SkillType.SPEED);
        refreshModifier(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, "bamboomod:speed_skill",
                0.01D * speedLv, AttributeModifier.Operation.MULTIPLY_TOTAL);
        int swimLv = SkillHelper.getLevel(player, SkillType.SWIM);
        refreshModifier(player, ForgeMod.SWIM_SPEED.get(), SWIM_UUID, "bamboomod:swim_skill",
                0.03D * swimLv, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 盾構え中のみ呼ぶ。非構え時は除去する。 */
    public static void applyShield(Player player, boolean blocking) {
        if (!blocking) {
            refreshModifier(player, Attributes.MOVEMENT_SPEED, SHIELD_UUID, "bamboomod:shield_skill",
                    0.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
            return;
        }
        int lv = SkillHelper.getLevel(player, SkillType.SHIELD);
        refreshModifier(player, Attributes.MOVEMENT_SPEED, SHIELD_UUID, "bamboomod:shield_skill",
                0.05D * lv, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
