//package real.inkognito338.murdermysteryutils.mixin;
//
//import net.minecraft.client.Minecraft;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import real.inkognito338.murdermysteryutils.modules.mm.TabListRoles;
//
//@Mixin(Minecraft.class)
//public class MixinMinecraft {
//
//    @Inject(method = "runTick", at = @At("HEAD"))
//    private void onRunTick(CallbackInfo ci) {
//        TabListRoles module = TabListRoles.getInstance();
//        if (module != null && module.isToggled() && module.isReady()) {
//            module.updateRolePrefixes();
//        }
//    }
//}