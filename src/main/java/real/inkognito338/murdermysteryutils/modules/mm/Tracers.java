package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;

public class Tracers extends Module {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc = Minecraft.getMinecraft();

    public Tracers() {
        super("Tracers");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        try {
            if (!this.isToggled() || mc.getRenderManager().options == null) {
                return;
            }

            EntityPlayerSP localPlayer = mc.player;
            if (localPlayer == null) return;

            // Получаем позицию камеры
            Vec3d cameraPos = getCameraPosition(localPlayer, event.getPartialTicks());

            for (EntityPlayer entity : mc.world.playerEntities) {
                if (entity != localPlayer && !entity.isInvisible()) {
                    renderTracer(entity, event.getPartialTicks(), cameraPos);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Tracers render error: ", e);
        }
    }

    private Vec3d getCameraPosition(EntityPlayerSP player, float partialTicks) {
        // Интерполируем позицию игрока
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Для вида от третьего лица учитываем смещение камеры
        if (mc.gameSettings.thirdPersonView > 0) {
            float distance = 4.0F; // Дистанция камеры от игрока
            float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;

            // Рассчитываем смещение камеры
            double offsetX = -Math.sin(Math.toRadians(yaw)) * distance;
            double offsetZ = Math.cos(Math.toRadians(yaw)) * distance;

            x += offsetX;
            z += offsetZ;
        }

        // Возвращаем позицию камеры (без добавления высоты глаз)
        return new Vec3d(x, y, z);
    }

    private void renderTracer(EntityPlayer player, float partialTicks, Vec3d cameraPos) {
        // Рассчитываем позицию цели с интерполяцией
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - cameraPos.x;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - cameraPos.y;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - cameraPos.z;

        // Устанавливаем цвет (красный по умолчанию)
        float red = 1.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float alpha = 0.8F;

        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(2.0F);

        // Рисуем линию от позиции камеры к игроку
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glVertex3d(0, 0, 0); // Начало от камеры
        GL11.glVertex3d(x, y, z); // Конец у ног игрока
        GL11.glEnd();

        // Восстанавливаем состояние OpenGL
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
    }
}