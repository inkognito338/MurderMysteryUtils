package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BarrierVision extends Module {

    private static BarrierVision INSTANCE;
    private TextureAtlasSprite barrierSprite;
    private final Minecraft mc = Minecraft.getMinecraft();

    public BarrierVision() {
        super("BarrierVision");
        INSTANCE = this;
    }

    public static BarrierVision getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        forceFullRenderUpdate();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        forceFullRenderUpdate();
    }

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.getMap() == mc.getTextureMapBlocks()) {
            barrierSprite = event.getMap().registerSprite(
                    new ResourceLocation("minecraft:blocks/barrier")
            );
            System.out.println("[BarrierVision] ✅ Зарегистрирован спрайт барьера");
        }
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        // Подменяем модель для инвентаря
        ModelResourceLocation model = new ModelResourceLocation("minecraft:barrier", "inventory");
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(Blocks.BARRIER), 0, model);
        System.out.println("[BarrierVision] ✅ Зарегистрирована модель барьера для инвентаря");
    }

    public TextureAtlasSprite getBarrierSprite() {
        return barrierSprite;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!isToggled()) return;
        // можно добавить кастомный рендер или эффекты
    }

    // Принудительное обновление всего мира для применения текстур/моделей
    private void forceFullRenderUpdate() {
        if (mc.player == null || mc.world == null) return;

        // Вариант 1: Обновить весь мир (наиболее эффективно для безлимитного радиуса)
        mc.renderGlobal.markBlockRangeForRenderUpdate(
                Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2,
                Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2
        );

        // Вариант 2: Альтернативный способ - обновить видимые чанки
        mc.renderGlobal.loadRenderers();

        // Вариант 3: Можно просто обновить все чанки вокруг игрока на максимальное расстояние
        // Максимальный радиус в Minecraft - 16 чанков для рендеринга
        int chunkRadius = 16;
        int chunkX = mc.player.getPosition().getX() >> 4;
        int chunkZ = mc.player.getPosition().getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                mc.world.markBlockRangeForRenderUpdate(
                        (chunkX + dx) << 4, 0, (chunkZ + dz) << 4,
                        ((chunkX + dx) << 4) + 15, 255, ((chunkZ + dz) << 4) + 15
                );
            }
        }
    }
}