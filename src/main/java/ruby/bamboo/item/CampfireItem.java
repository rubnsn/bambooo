package ruby.bamboo.item;

import java.util.function.Consumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import ruby.bamboo.block.entity.CampfireItemRenderer;

/**
 * 囲炉裏の BlockItem。
 * <p>
 * 旧 item モデルは builtin/entity で BEWLR 描画のため、IClientItemExtensions で
 * カスタムレンダラーを登録する。
 */
public class CampfireItem extends BlockItem {

    public CampfireItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CampfireItemRenderer.getInstance();
            }
        });
    }
}