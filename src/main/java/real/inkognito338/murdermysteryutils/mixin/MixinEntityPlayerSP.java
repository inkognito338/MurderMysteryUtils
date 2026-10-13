package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import real.inkognito338.murdermysteryutils.modules.ChatColor;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 09.08.2026
 */

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    private static final boolean DEBUG = false;

    @Inject(
            method = "sendChatMessage",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendChatMessage(String message, CallbackInfo ci) {
        ChatColor chatColor = ChatColor.getInstance();

        if (DEBUG) System.out.println("sendChatMessage called: \"" + message + "\", instance=" + chatColor);

        if (chatColor == null) {
            if (DEBUG) System.out.println("ChatColor instance null, skipping");
            return;
        }

        String currentMode = chatColor.getCurrentMode();
        if (DEBUG) System.out.println("mode=" + currentMode);

        if (!"Packet".equals(currentMode)) {
            if (DEBUG) System.out.println("Skipping, mode is not 'Packet'");
            return;
        }

        if (message == null || message.isEmpty()) {
            return;
        }

        String processed = chatColor.processMessageFromMixin(message);

        if (processed != null && !processed.isEmpty() && !processed.equals(message)) {
            ci.cancel();

            EntityPlayerSP player = (EntityPlayerSP) (Object) this;
            player.sendChatMessage(processed);
        }
    }
}