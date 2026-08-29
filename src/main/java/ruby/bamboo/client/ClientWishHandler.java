package ruby.bamboo.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ruby.bamboo.client.gui.WishScreen;

@OnlyIn(Dist.CLIENT)
public class ClientWishHandler {

    public static void handleOpen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.0F));
                mc.gui.setTitle(Component.translatable("bamboomod.wish.title").withStyle(ChatFormatting.GOLD));
                mc.gui.setTimes(10, 70, 20);
            }
            mc.setScreen(new WishScreen());
        });
    }
}
