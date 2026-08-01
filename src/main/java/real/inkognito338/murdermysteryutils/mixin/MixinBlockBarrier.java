package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.block.BlockBarrier;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import real.inkognito338.murdermysteryutils.modules.BarrierVision;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@Mixin(BlockBarrier.class)
public abstract class MixinBlockBarrier {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void onGetRenderType(IBlockState state, CallbackInfoReturnable<EnumBlockRenderType> cir) {
        if (BarrierVision.getInstance() != null && BarrierVision.getInstance().isToggled()) {
            cir.setReturnValue(EnumBlockRenderType.MODEL);
        }
    }
}