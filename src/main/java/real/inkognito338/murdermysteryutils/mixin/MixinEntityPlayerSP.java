package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import real.inkognito338.murdermysteryutils.modules.ChatColor;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 09.08.2026
 */

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    @ModifyVariable(
            method = "sendChatMessage",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private String onSendChatMessage(String message) {
        ChatColor chatColor = ChatColor.getInstance();
        if (chatColor == null || !chatColor.isPacketModeActive()) return message;
        return chatColor.processMessageFromMixin(message);
    }
}