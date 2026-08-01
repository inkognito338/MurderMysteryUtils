package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import real.inkognito338.murdermysteryutils.modules.NameProtect;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

    @ModifyArg(
            method = "printChatMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiNewChat;printChatMessageWithOptionalDeletion(Lnet/minecraft/util/text/ITextComponent;I)V",
                    ordinal = 0
            ),
            index = 0
    )
    private ITextComponent replaceChatMessage(ITextComponent message) {
        NameProtect module = NameProtect.getInstance();
        if (module != null && module.isToggled()) {
            String text = message.getUnformattedText();
            String real = NameProtect.getRealName();
            if (!real.isEmpty() && text.contains(real)) {
                String replaced = text.replace(real, NameProtect.getFakeName());
                return new TextComponentString(replaced);
            }
        }
        return message;
    }
}