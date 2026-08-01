package real.inkognito338.murdermysteryutils.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import real.inkognito338.murdermysteryutils.modules.CustomWeather;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

@Mixin(net.minecraft.client.renderer.EntityRenderer.class)
public abstract class RenderWeatherMixin {

    /**
     * renderRainSnow() вычисляет высоту осадков через getPrecipitationHeight().
     * Если эта высота выше игрока (блок над ним), то k2 == l2 и столбец пропускается.
     * Подменяем возвращаемый BlockPos: опускаем Y до уровня игрока,
     * чтобы снег рисовался даже под крышей.
     */
    @Redirect(
            method = "renderRainSnow",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getPrecipitationHeight(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"
            )
    )
    private BlockPos redirectGetPrecipitationHeight(World world, BlockPos pos) {
        BlockPos original = world.getPrecipitationHeight(pos);
        CustomWeather module = CustomWeather.INSTANCE;
        if (module != null && module.isSnowMode()) {
            // Возвращаем позицию прямо над игроком (Y = pos.getY()),
            // так k2/l2 не будут равны и столбец будет отрисован.
            return new BlockPos(original.getX(), pos.getY(), original.getZ());
        }
        return original;
    }
}