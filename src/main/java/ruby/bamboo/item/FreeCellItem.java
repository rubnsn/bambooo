package ruby.bamboo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import ruby.bamboo.network.BambooNetwork;
import ruby.bamboo.network.FreeCellOpenPacket;

/**
 * フリーセル起動札 (テスト用)。
 * 右クリックでフリーセル画面を開く。ゲーム状態はクライアント側のみで持つ。
 */
public class FreeCellItem extends Item {

    public FreeCellItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BambooNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new FreeCellOpenPacket());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
