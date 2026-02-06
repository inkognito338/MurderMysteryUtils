package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import java.util.Map;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class FlowerPotESP extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public FlowerPotESP() {
        super("FlowerPotESP");

        String[] modes = {"WithFlowers", "WithoutFlowers", "All"};
        this.addSetting(new Setting("Mode", SettingType.MODE, "WithFlowers", modes));
        this.addSetting(new Setting("Distance", SettingType.NUMBER, 50.0, 5.0, 1000.0));
        this.addSetting(new Setting("BoxSize", SettingType.NUMBER, 0.5, 0.1, 5.0));

        // Заменяем отдельные настройки Red, Green, Blue на единую COLOR
        this.addSetting(new Setting("Color", SettingType.COLOR, new float[]{1.0f, 0.0f, 1.0f}));
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
        double maxDistanceSq = maxDistance * maxDistance;
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
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glLineWidth(2.0f);

        BlockPos playerPos = mc.player.getPosition();
        int radius = (int) maxDistance;

        int chunkStartX = (playerPos.getX() - radius) >> 4;
        int chunkEndX = (playerPos.getX() + radius) >> 4;
        int chunkStartZ = (playerPos.getZ() - radius) >> 4;
        int chunkEndZ = (playerPos.getZ() + radius) >> 4;

        for (int cx = chunkStartX; cx <= chunkEndX; cx++) {
            for (int cz = chunkStartZ; cz <= chunkEndZ; cz++) {
                Chunk chunk = mc.world.getChunkFromChunkCoords(cx, cz);
                if (chunk == null || !chunk.isLoaded()) continue;

                Map<BlockPos, TileEntity> tileEntityMap = chunk.getTileEntityMap();
                for (TileEntity tileEntity : tileEntityMap.values()) {
                    if (tileEntity instanceof TileEntityFlowerPot) {
                        BlockPos pos = tileEntity.getPos();
                        if (playerPos.distanceSq(pos) > maxDistanceSq) continue;

                        IBlockState state = mc.world.getBlockState(pos);
                        checkAndRenderFlowerPot(pos, state, (TileEntityFlowerPot) tileEntity, mode,
                                boxSize, red, green, blue, alpha, outline);
                    }
                }
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void checkAndRenderFlowerPot(BlockPos pos, IBlockState state, TileEntityFlowerPot tileEntity,
                                         String mode, double boxSize, float r, float g, float b, float a, boolean outline) {
        boolean hasFlower = isFlowerPotNotEmpty(tileEntity);

        if (mode.equals("WithFlowers") && !hasFlower) return;
        if (mode.equals("WithoutFlowers") && hasFlower) return;

        drawBox(pos, boxSize, r, g, b, a, outline);
    }

    private boolean isFlowerPotNotEmpty(TileEntityFlowerPot tileEntity) {
        try {
            if (tileEntity.getFlowerPotItem() != null) {
                String itemName = tileEntity.getFlowerPotItem().getUnlocalizedName();
                if (itemName != null && !itemName.contains("air") && !itemName.isEmpty()) {
                    return true;
                }
            }

            if (tileEntity.getFlowerItemStack() != null && !tileEntity.getFlowerItemStack().isEmpty()) {
                return true;
            }

            String displayName = tileEntity.getDisplayName().getUnformattedText();
            if (displayName != null && !displayName.contains("Flower Pot") && !displayName.equals("")) {
                return true;
            }

            IBlockState state = mc.world.getBlockState(tileEntity.getPos());
            if (state.getBlock() instanceof BlockFlowerPot) {
                BlockFlowerPot.EnumFlowerType flowerType = state.getValue(BlockFlowerPot.CONTENTS);
                return flowerType != BlockFlowerPot.EnumFlowerType.EMPTY;
            }

        } catch (Exception e) {
            return hasFlowerByBlockState(tileEntity.getPos());
        }

        return false;
    }

    private boolean hasFlowerByBlockState(BlockPos pos) {
        IBlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof BlockFlowerPot) {
            try {
                BlockFlowerPot.EnumFlowerType flowerType = state.getValue(BlockFlowerPot.CONTENTS);
                return flowerType != BlockFlowerPot.EnumFlowerType.EMPTY;
            } catch (Exception e) {
                return state.getBlock().getMetaFromState(state) != 0;
            }
        }
        return false;
    }

    private void drawBox(BlockPos pos, double size, float r, float g, float b, float a, boolean outline) {
        double x = pos.getX() - mc.getRenderManager().viewerPosX + 0.5;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ + 0.5;

        double half = size / 2.0;

        AxisAlignedBB box = new AxisAlignedBB(
                x - half, y, z - half,
                x + half, y + size, z + half
        );
        GlStateManager.disableDepth();

        if (outline) {
            RenderGlobal.drawSelectionBoundingBox(box, r, g, b, a);
        } else {
            RenderGlobal.renderFilledBox(box, r, g, b, a);
        }

        GlStateManager.enableDepth();
    }

}