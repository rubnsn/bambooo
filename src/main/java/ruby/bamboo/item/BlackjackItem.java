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
import ruby.bamboo.network.BlackjackOpenPacket;

/**
 * ブラックジャック起動札 (テスト用)。
 * 右クリックでブラックジャック画面を開く。フリーはクライアントのみ、
 * ベットはサーバーでエメラルド=点を管理する。
 */
public class BlackjackItem extends Item {

    public BlackjackItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BambooNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new BlackjackOpenPacket());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
