package ruby.bamboo.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import ruby.bamboo.client.gui.MiniatureConfigScreen;

/**
 * クライアント専用の ConfigScreen 登録ヘルパー。
 * <p>
 * {@link ruby.bamboo.BambooMod} から直接 {@code Screen} を参照すると、
 * DEDICATED_SERVER で RuntimeDistCleaner が {@code net/minecraft/client/gui/screens/Screen}
 * のロードを検出してクラッシュするため、分離して {@code DistExecutor} 経由で呼び出す。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientConfigRegistration {

    private ClientConfigRegistration() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, screen) -> new MiniatureConfigScreen(screen)));
    }
}
