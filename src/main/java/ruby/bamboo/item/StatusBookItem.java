package ruby.bamboo.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.skill.SkillHelper;
import ruby.bamboo.skill.SkillType;

/**
 * ステータス本 (feat-spec-skill §5)。
 * 初スキル取得時に1冊配布 + 常時クラフト可。
 * GUI完成まではチャットに一覧表示する。
 */
public class StatusBookItem extends Item {

    public StatusBookItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SkillHelper.syncTo(sp);
            SkillHelper.get(sp).ifPresent(s -> {
                for (SkillType t : SkillType.values()) {
                    sp.displayClientMessage(Component.literal(
                            t.getId() + " Lv" + s.getLevel(t) + " " + s.getXp(t) + "/" + s.getNext(t)), false);
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
