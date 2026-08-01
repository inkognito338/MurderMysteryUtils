package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import real.inkognito338.murdermysteryutils.modules.ShowNames;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Inject(method = "canRenderName", at = @At("HEAD"), cancellable = true)
    private void onCanRenderName(T entity, CallbackInfoReturnable<Boolean> cir) {
        ShowNames mod = ShowNames.getInstance();
        if (mod == null || !mod.isToggled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;
        if (player.isSpectator()) return;
        if (entity.getDistance(player) > 64) return;

        cir.setReturnValue(true);
    }
}