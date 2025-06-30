package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

// by inkognito338 | forge 1.12.2 - 14.23.5.2860


public class NameRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    // Обработчик события RenderWorldLastEvent
    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {

        if (!SettingsOption.NAME_TAGS.getValue()) {
            return;
        }

        // Получаем текущего игрока
        EntityPlayerSP player = mc.player;

        // Рендерим имена для всех игроков в мире
        for (EntityPlayer entity : mc.world.playerEntities) {
            if (entity != player) {
                renderPlayerName(entity, event.getPartialTicks());
            }
        }
    }

    // Функция для рендеринга имени игрока
    public void renderPlayerName(EntityPlayer player, float partialTicks) {
        String name = player.getName();

        if (name == null) {
            return;  // Если имя игрока отсутствует, ничего не делаем
        }

        // Получаем координаты игрока для рендеринга имени
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY + player.height + 0.5;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        // Получаем объект FontRenderer для рисования текста
        FontRenderer fontRenderer = mc.fontRenderer;

        // Отключаем проверку глубины, чтобы текст был видимым через стены
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        // Повороты для текста
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);

        // Масштабирование для уменьшения размера текста
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);

        // Получаем ширину строки, чтобы центрировать текст
        int stringWidth = fontRenderer.getStringWidth(name);

        // Отключаем глубину (чтобы имена не скрывались за объектами)
        GlStateManager.disableDepth();

        // Рисуем имя с тенью
        fontRenderer.drawStringWithShadow(name, -stringWidth / 2f, 0, 0xFFFFFF);

        // Включаем глубину обратно
        GlStateManager.enableDepth();

        GlStateManager.popMatrix();
    }
}
