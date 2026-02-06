package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import real.inkognito338.murdermysteryutils.modules.mm.ShowNames;


@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Inject(method = "canRenderName", at = @At("HEAD"), cancellable = true)
    private void onCanRenderName(T entity, CallbackInfoReturnable<Boolean> cir) {

        ShowNames mod = ShowNames.getInstance();
        if (mod == null || !mod.isToggled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (player == null) return;

        // Не показывать если мы в спектаторе
        if (player.isSpectator()) return;

        // Не показывать имена слишком далеко
        if (entity.getDistance(player) > 64) return;

        // Проверка видимости без сквозных стен
//        if (!canSeeEntity(player, entity)) return;

        cir.setReturnValue(true);
    }

    private boolean canSeeEntity(EntityPlayerSP viewer, Entity target) {

        Vec3d eyes = new Vec3d(
                viewer.posX,
                viewer.posY + viewer.getEyeHeight(),
                viewer.posZ
        );

        Vec3d targetPos = new Vec3d(
                target.posX,
                target.posY + target.height * 0.5,
                target.posZ
        );

        RayTraceResult result = viewer.world.rayTraceBlocks(
                eyes,
                targetPos,
                false,
                true,
                false
        );

        return result == null || result.typeOfHit == RayTraceResult.Type.MISS;
    }
}
