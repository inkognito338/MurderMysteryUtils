//package real.inkognito338.murdermysteryutils.mixin;
//
//import net.minecraft.client.gui.GuiPlayerTabOverlay;
//import net.minecraft.client.network.NetworkPlayerInfo;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//import real.inkognito338.murdermysteryutils.modules.TabListRoles;
//
///**
// * Project: MurderMysteryUtils
// * Author: inkognito338
// * Date: 10.07.2026
// */
//
//@Mixin(GuiPlayerTabOverlay.class)
//public abstract class MixinGuiPlayerTabOverlay {
//
//    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
//    private void onGetPlayerName(NetworkPlayerInfo info, CallbackInfoReturnable<String> cir) {
//        TabListRoles module = TabListRoles.getInstance();
//        if (module == null || !module.isToggled()) return;
//
//        String name = info.getGameProfile().getName();
//        if (name == null) return;
//
//        String original = cir.getReturnValue();
//        if (original == null) return;
//
//        String modified = TabListRoles.getModifiedTabName(name, original);
//
//        if (modified != null) {
//            cir.setReturnValue(modified);
//        }
//    }
//}