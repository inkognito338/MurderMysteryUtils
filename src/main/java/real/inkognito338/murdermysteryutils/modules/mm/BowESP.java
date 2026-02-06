package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class BowESP extends Module {
    private final Minecraft mc = Minecraft.getMinecraft();

    public BowESP() {
        super("BowESP");

        String[] modes = {"Dropped", "Stand", "Both"};
        this.addSetting(new Setting("Mode", SettingType.MODE, "Dropped", modes));

        this.addSetting(new Setting("Distance", SettingType.NUMBER, 50.0, 5.0, 1000.0));
        this.addSetting(new Setting("BoxSize", SettingType.NUMBER, 0.5, 0.1, 100.0));

        // Заменяем отдельные настройки Red, Green, Blue на единую COLOR
        this.addSetting(new Setting("Color", SettingType.COLOR, new float[]{1.0f, 0.48f, 0.0f}));
        this.addSetting(new Setting("Alpha", SettingType.NUMBER, 160.0, 0.0, 255.0));

        this.addSetting(new Setting("Outline", SettingType.BOOLEAN, true));
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null) return;

        String mode = (String) getSettingByName("Mode").getValue();
        double maxDistance = (double) getSettingByName("Distance").getValue();
        double boxSize = (double) getSettingByName("BoxSize").getValue();

        // Получаем цвет из единой настройки COLOR
        float[] color = (float[]) getSettingByName("Color").getValue();
        float red = color[0];
        float green = color[1];
        float blue = color[2];

        float alpha = (float) ((double) getSettingByName("Alpha").getValue() / 255f);
        boolean outline = (boolean) getSettingByName("Outline").getValue();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();

        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == mc.player) continue;
            double dist = mc.player.getDistance(entity);
            if (dist > maxDistance) continue;

            if ((mode.equalsIgnoreCase("Dropped") || mode.equalsIgnoreCase("Both")) && entity instanceof EntityItem) {
                EntityItem item = (EntityItem) entity;
                ItemStack stack = item.getItem();
                if (stack.getItem() == Items.BOW) {
                    drawBowBox(entity, red, green, blue, alpha, boxSize, outline, true);
                }
            }

            if ((mode.equalsIgnoreCase("Stand") || mode.equalsIgnoreCase("Both")) && entity instanceof EntityArmorStand) {
                EntityArmorStand stand = (EntityArmorStand) entity;
                if (hasBow(stand)) {
                    drawBowBox(stand, red, green, blue, alpha, boxSize, outline, false);
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private boolean hasBow(EntityArmorStand stand) {
        for (ItemStack stack : stand.getArmorInventoryList()) {
            if (stack != null && stack.getItem() == Items.BOW) return true;
        }
        ItemStack main = stand.getHeldItemMainhand();
        ItemStack off = stand.getHeldItemOffhand();
        return (main != null && main.getItem() == Items.BOW) || (off != null && off.getItem() == Items.BOW);
    }

    private void drawBowBox(Entity e, float r, float g, float b, float a, double size, boolean outline, boolean isDropped) {
        double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosX;
        double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosY;
        double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * mc.getRenderPartialTicks() - mc.getRenderManager().viewerPosZ;

        double centerX = x;
        double centerY;
        double centerZ = z;

        if (isDropped) {
            // Дропнутый лук — чуть выше пола
            centerY = y + 0.25;
        } else {
            // Для ArmorStand — найти руку с луком
            EntityArmorStand stand = (EntityArmorStand) e;

            ItemStack main = stand.getHeldItemMainhand();
            ItemStack off = stand.getHeldItemOffhand();

            if (main != null && main.getItem() == Items.BOW) {
                centerX = x + 0.3; // сдвиг вправо от центра стойки (примерно)
            } else if (off != null && off.getItem() == Items.BOW) {
                centerX = x - 0.3; // сдвиг влево от центра
            }

            centerY = y + e.height * 0.9; // примерно на уровне руки
            centerZ = z; // остаётся на той же оси Z
        }

        double half = size / 2.0;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0f);

        net.minecraft.util.math.AxisAlignedBB box = new net.minecraft.util.math.AxisAlignedBB(
                centerX - half, centerY - half, centerZ - half,
                centerX + half, centerY + half, centerZ + half
        );

        if (outline) {
            RenderGlobal.drawSelectionBoundingBox(box, r, g, b, a);
        } else {
            RenderGlobal.renderFilledBox(box, r, g, b, a);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}