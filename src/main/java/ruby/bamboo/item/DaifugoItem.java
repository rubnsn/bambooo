package ruby.bamboo.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ruby.bamboo.client.handler.ClientDaifugoHandler;
import ruby.bamboo.daifugo.DaifugoManager;

/**
 * 大富豪札 (テスト用)。
 * 右クリックで参加受付中の部屋に参加、なければ作成 (再入場も兼ねる)。
 * 画面はサーバーからのスナップショットで開く。
 */
public class DaifugoItem extends Item {

    public DaifugoItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && sp.getServer() != null) {
            DaifugoManager.joinOrCreate(sp.getServer(), sp);
        } else if (level.isClientSide) {
            // 再開指示 (ESC等で閉じた後の自動オープン抑止を解除する)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientDaifugoHandler.expectOpen());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
