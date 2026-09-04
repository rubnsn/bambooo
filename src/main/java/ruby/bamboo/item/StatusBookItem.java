package ruby.bamboo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ruby.bamboo.skill.SkillHelper;

/**
 * ステータス本 (feat-spec-skill §5)。
 * 初スキル取得時に1冊配布 + 常時クラフト可。
 * GUI完成まではチャットに一覧表示する。
 */
public class StatusBookItem extends Item {

    public StatusBookItem(Properties props) {
        super(props);
    }

    /** エンチャント不可 (金床・テーブルとも)。 */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SkillHelper.syncTo(sp);
            ruby.bamboo.network.BambooNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new ruby.bamboo.network.SkillStatusOpenPacket());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
