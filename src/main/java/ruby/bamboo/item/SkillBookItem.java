package ruby.bamboo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.skill.SkillReading;
import ruby.bamboo.skill.SkillType;

/**
 * スキル本 (feat-spec-skill §4)。耐久5、右クリックで読書開始。
 * 成功で Lv+1・本消滅、失敗で耐久-1 + デメリット + 5秒クール。
 */
public class SkillBookItem extends Item {

    private final SkillType skill;

    public SkillBookItem(SkillType skill, Properties props) {
        super(props);
        this.skill = skill;
    }

    public SkillType getSkill() {
        return skill;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SkillReading.start(sp, skill, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
