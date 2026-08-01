package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import real.inkognito338.murdermysteryutils.modules.NameProtect;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 10.07.2026
 */

@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

    @ModifyArg(
            method = "renderString",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;renderStringAtPos(Ljava/lang/String;Z)V",
                    ordinal = 0
            ),
            index = 0
    )
    private String replaceName(String text) {
        NameProtect module = NameProtect.getInstance();
        if (module != null && module.isToggled() && text != null) {
            String real = NameProtect.getRealName();
            if (!real.isEmpty()) {
                return text.replace(real, NameProtect.getFakeName());
            }
        }
        return text;
    }
}