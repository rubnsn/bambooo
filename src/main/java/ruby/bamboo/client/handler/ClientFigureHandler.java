package ruby.bamboo.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.FigurePoseScreen;
import ruby.bamboo.entity.FigureEntity;

/**
 * クライアント側 フィギュアGUI開放。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFigureHandler {

    private ClientFigureHandler() {
    }

    public static void openFigure(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null) return;
            var entity = mc.level.getEntity(entityId);
            if (!(entity instanceof FigureEntity figure)) return;
            mc.setScreen(new FigurePoseScreen(figure));
        });
    }
}
