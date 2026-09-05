package ruby.bamboo.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import ruby.bamboo.client.gui.MiniatureConfigScreen;

/**
 * クライアント専用の ConfigScreen 登録ヘルパー。
 * <p>
 * {@link ruby.bamboo.BambooMod} から直接 {@code Screen} を参照すると、
 * DEDICATED_SERVER でクライアントクラスのロードを検出してクラッシュするため、
 * BambooMod 側の dist 分岐内からのみ呼び出す (DistExecutor は 1.21 で削除)。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientConfigRegistration {

    private ClientConfigRegistration() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, screen) -> new MiniatureConfigScreen(screen));
    }
}
