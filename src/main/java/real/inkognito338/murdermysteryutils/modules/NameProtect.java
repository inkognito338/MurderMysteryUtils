package real.inkognito338.murdermysteryutils.modules;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import real.inkognito338.murdermysteryutils.utils.Module;
import real.inkognito338.murdermysteryutils.utils.settings.Setting;
import real.inkognito338.murdermysteryutils.utils.settings.SettingType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 10.07.2026
 */

@SideOnly(Side.CLIENT)
public class NameProtect extends Module {

    private static final ResourceLocation STEVE_SKIN =
            new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX_SKIN =
            new ResourceLocation("textures/entity/alex.png");
    private static NameProtect INSTANCE;
    private static String realName = "";
    private static String fakeName = "mmutils-fakename";
    private final Minecraft mc = Minecraft.getMinecraft();

    // ── Кэш для скинов ──────────────────────────────────────────────────────
    private final ConcurrentMap<UUID, ResourceLocation> mmutilsTextures = new ConcurrentHashMap<>();
    private final ConcurrentMap<ResourceLocation, ITextureObject> originalTextures = new ConcurrentHashMap<>();
    private String originalSkinType = null;
    private String lastAppliedModel = null;

    // ── Кэш для плаща ────────────────────────────────────────────────────────
    private ResourceLocation cachedCapeLocation = null;
    private boolean capeHidden = false;

    // Настройки
    private final Setting fakeNameSetting;
    private final Setting hideSkin;
    private final Setting skinModel;
    private final Setting hideCape;

    // ── Reflection ──────────────────────────────────────────────────────────
    private Field prefixField;
    private Field suffixField;
    private Field skinTypeField;
    private Field playerTexturesField; // Map<MinecraftProfileTexture.Type, ResourceLocation> в NetworkPlayerInfo (1.9+)
    private boolean reflectionReady = false;
    private boolean capeReflectionReady = false;

    public NameProtect() {
        super("NameProtect");
        INSTANCE = this;

        this.fakeNameSetting = new Setting("FakeName", SettingType.TEXT, fakeName);
        this.hideSkin = new Setting("HideSkin", SettingType.BOOLEAN, true);
        this.skinModel = new Setting("SkinModel", SettingType.MODE, "Steve",
                new String[]{"Steve", "Alex"}
        );
        this.hideCape = new Setting("HideCape", SettingType.BOOLEAN, true);

        this.addSetting(fakeNameSetting);
        this.addSetting(hideSkin);
        this.addSetting(skinModel);
        this.addSetting(hideCape);

        setupReflection();
        detectName();
    }

    public static NameProtect getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NameProtect();
        }
        return INSTANCE;
    }

    public static String getRealName() {
        return realName;
    }

    public static String getFakeName() {
        if (INSTANCE != null) {
            String settingName = (String) INSTANCE.fakeNameSetting.getValue();
            if (settingName != null && !settingName.isEmpty()) {
                return settingName;
            }
        }
        return fakeName;
    }

    private void setupReflection() {
        try {
            prefixField = ScorePlayerTeam.class.getDeclaredField("prefix");
            suffixField = ScorePlayerTeam.class.getDeclaredField("suffix");
            prefixField.setAccessible(true);
            suffixField.setAccessible(true);

            // Рефлексия типа модели (Slim/Alex или Default/Steve)
            try {
                skinTypeField = NetworkPlayerInfo.class.getDeclaredField("skinType");
            } catch (NoSuchFieldException e) {
                // Имя поля для обфусцированной среды Minecraft (1.8.9 / 1.12.2)
                skinTypeField = NetworkPlayerInfo.class.getDeclaredField("field_178854_g");
            }
            skinTypeField.setAccessible(true);

            reflectionReady = true;
        } catch (Exception e) {
            reflectionReady = false;
        }

        try {
            // В 1.9+ (в т.ч. 1.12.2) плащ хранится не в отдельном поле locationCape,
            // а в Map<MinecraftProfileTexture.Type, ResourceLocation> playerTextures.
            try {
                playerTexturesField = NetworkPlayerInfo.class.getDeclaredField("playerTextures");
            } catch (NoSuchFieldException e) {
                // SRG-имя для 1.12.2
                playerTexturesField = NetworkPlayerInfo.class.getDeclaredField("field_187107_a");
            }
            playerTexturesField.setAccessible(true);
            capeReflectionReady = true;
        } catch (Exception e) {
            capeReflectionReady = false;
        }
    }

    private void detectName() {
        if (mc.player != null) {
            realName = mc.player.getName();
        }
    }

    @Override
    public void onEnable() {
        detectName();
        String settingName = (String) fakeNameSetting.getValue();
        if (settingName != null && !settingName.isEmpty()) {
            fakeName = settingName;
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        restoreOriginalSkin();
        restoreOriginalCape();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null) return;

        // Фикс проблемы №1: Если модуль выключен ИЛИ галочка "HideSkin" снята в меню — мгновенно восстанавливаем оригинальный скин
        if (!isToggled() || !(boolean) hideSkin.getValue()) {
            if (!originalTextures.isEmpty()) {
                restoreOriginalSkin();
            }
        }

        // Если модуль выключен ИЛИ галочка "HideCape" снята — мгновенно восстанавливаем оригинальный плащ
        if (!isToggled() || !(boolean) hideCape.getValue()) {
            if (capeHidden) {
                restoreOriginalCape();
            }
        }

        if (!isToggled()) return;

        if (realName.isEmpty()) {
            detectName();
            return;
        }

        String settingName = (String) fakeNameSetting.getValue();
        if (settingName != null && !settingName.isEmpty()) {
            fakeName = settingName;
        }

        if (reflectionReady) {
            replaceScoreboardNames();
        }

        if (capeReflectionReady && (boolean) hideCape.getValue()) {
            applyCapeHide();
        }
    }

    /**
     * Эвент рендера игрока (для третьего лица и других игроков)
     */
    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!isToggled()) return;
        if (mc.player == null) return;

        EntityPlayer entity = event.getEntityPlayer();
        if (entity == null || !entity.getUniqueID().equals(mc.player.getUniqueID())) return;

        if ((boolean) hideSkin.getValue()) {
            applySkinReplacement();
        }
        if ((boolean) hideCape.getValue()) {
            applyCapeHide();
        }
    }

    /**
     * Эвент рендера рук/предметов в первом лице.
     * Решает проблему №2: рука теперь обновляется на лету без F5 и перезаходов!
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (!isToggled()) return;
        if (!(boolean) hideSkin.getValue()) return;
        if (mc.player == null) return;

        applySkinReplacement();
    }

    /**
     * Общая логика подмены текстуры и модели
     */
    private void applySkinReplacement() {
        try {
            if (mc.getConnection() == null) return;
            UUID playerId = mc.player.getUniqueID();
            NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(playerId);
            if (playerInfo == null) return;

            ResourceLocation originalSkin = playerInfo.getLocationSkin();
            if (originalSkin == null) return;

            // 1. Бэкапим оригинальный объект текстуры
            if (!originalTextures.containsKey(originalSkin)) {
                ITextureObject originalTexture = mc.getTextureManager().getTexture(originalSkin);
                if (originalTexture != null) {
                    originalTextures.put(originalSkin, originalTexture);
                }
            }

            // 2. Меняем тип модели (Steve/Alex), чтобы мгновенно обновилась ширина руки в 1-м лице
            String modelSetting = (String) skinModel.getValue();
            if (modelSetting == null) modelSetting = "Steve";
            String expectedType = "Alex".equalsIgnoreCase(modelSetting) ? "slim" : "default";

            if (skinTypeField != null) {
                String currentType = (String) skinTypeField.get(playerInfo);
                if (!expectedType.equals(currentType)) {
                    if (originalSkinType == null) {
                        originalSkinType = currentType;
                    }
                    skinTypeField.set(playerInfo, expectedType);
                }
            }

            // 3. Создаем/обновляем фейковую текстуру (пересоздаем, если изменился тип модели)
            ResourceLocation fakeSkin = mmutilsTextures.get(playerId);
            if (fakeSkin == null || !modelSetting.equals(lastAppliedModel)) {
                if (fakeSkin != null) {
                    mc.getTextureManager().deleteTexture(fakeSkin);
                }
                fakeSkin = createFakeSkin(playerId);
                if (fakeSkin != null) {
                    mmutilsTextures.put(playerId, fakeSkin);
                    lastAppliedModel = modelSetting;
                }
            }

            // 4. Принудительно заменяем указатель в TextureManager прямо перед рендером кадра
            if (fakeSkin != null) {
                replaceTextureInManager(originalSkin, fakeSkin);
            }

        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    /**
     * Скрывает плащ локального игрока, убирая запись CAPE из карты
     * playerTextures в NetworkPlayerInfo. AbstractClientPlayer.getLocationCape()
     * в этом случае возвращает null, и рендерер плаща просто пропускает отрисовку.
     *
     * Важно: в 1.9+ (включая 1.12.2) плащ хранится не в отдельном поле
     * locationCape, а в Map<MinecraftProfileTexture.Type, ResourceLocation>
     * playerTextures — именно поэтому старый подход через locationCape
     * не работал на 1.12.2.
     */
    @SuppressWarnings("unchecked")
    private void applyCapeHide() {
        try {
            if (!capeReflectionReady) return;
            if (mc.getConnection() == null || mc.player == null) return;

            UUID playerId = mc.player.getUniqueID();
            NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(playerId);
            if (playerInfo == null) return;

            Map<MinecraftProfileTexture.Type, ResourceLocation> textures =
                    (Map<MinecraftProfileTexture.Type, ResourceLocation>) playerTexturesField.get(playerInfo);
            if (textures == null) return;

            ResourceLocation currentCape = textures.get(MinecraftProfileTexture.Type.CAPE);

            // Уже скрыт — ничего не делаем
            if (currentCape == null) {
                capeHidden = true;
                return;
            }

            // Сохраняем оригинал только один раз, чтобы не затереть его повторным вызовом
            if (!capeHidden) {
                cachedCapeLocation = currentCape;
            }

            textures.remove(MinecraftProfileTexture.Type.CAPE);
            capeHidden = true;

        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    /**
     * Восстанавливает оригинальную запись CAPE в карте playerTextures
     */
    @SuppressWarnings("unchecked")
    private void restoreOriginalCape() {
        try {
            if (!capeReflectionReady) return;
            if (!capeHidden || cachedCapeLocation == null) {
                capeHidden = false;
                cachedCapeLocation = null;
                return;
            }

            if (mc.getConnection() != null && mc.player != null) {
                NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
                if (playerInfo != null) {
                    Map<MinecraftProfileTexture.Type, ResourceLocation> textures =
                            (Map<MinecraftProfileTexture.Type, ResourceLocation>) playerTexturesField.get(playerInfo);
                    if (textures != null) {
                        textures.put(MinecraftProfileTexture.Type.CAPE, cachedCapeLocation);
                    }
                }
            }

            capeHidden = false;
            cachedCapeLocation = null;

        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    /**
     * Создает фейковый скин (Steve или Alex) как DynamicTexture
     */
    private ResourceLocation createFakeSkin(UUID playerId) {
        try {
            String model = (String) skinModel.getValue();
            if (model == null) model = "Steve";

            boolean slim = "Alex".equalsIgnoreCase(model);
            ResourceLocation templateSkin = slim ? ALEX_SKIN : STEVE_SKIN;

            try (InputStream inputStream = mc.getResourceManager().getResource(templateSkin).getInputStream()) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) return templateSkin;

                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);

                ResourceLocation resourceLocation = new ResourceLocation("mmutils",
                        "fake_skin_" + playerId.toString().replace("-", ""));

                if (mc.getTextureManager().getTexture(resourceLocation) != null) {
                    return resourceLocation;
                }

                DynamicTexture dynamicTexture = new DynamicTexture(width, height);
                System.arraycopy(pixels, 0, dynamicTexture.getTextureData(), 0, pixels.length);
                dynamicTexture.updateDynamicTexture();

                mc.getTextureManager().loadTexture(resourceLocation, dynamicTexture);

                return resourceLocation;

            } catch (Exception e) {
                return templateSkin;
            }

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Заменяет текстуру в TextureManager
     */
    private void replaceTextureInManager(ResourceLocation originalLocation, ResourceLocation newTexture) {
        try {
            if (originalLocation == null || newTexture == null) return;

            ITextureObject texture = mc.getTextureManager().getTexture(newTexture);
            if (texture != null) {
                mc.getTextureManager().loadTexture(originalLocation, texture);
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    /**
     * Чисто и безопасно восстанавливает оригинальный скин и модель рук
     */
    private void restoreOriginalSkin() {
        try {
            // 1. Возвращаем оригинальные объекты текстур назад в менеджер
            for (Map.Entry<ResourceLocation, ITextureObject> entry : originalTextures.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    mc.getTextureManager().loadTexture(entry.getKey(), entry.getValue());
                }
            }

            // 2. Восстанавливаем оригинальную толщину рук (модель) персонажа
            if (mc.player != null && originalSkinType != null && skinTypeField != null && mc.getConnection() != null) {
                NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
                if (playerInfo != null) {
                    skinTypeField.set(playerInfo, originalSkinType);
                }
            }

            // 3. Стираем временные текстуры из памяти
            if (mc.player != null) {
                ResourceLocation fakeSkin = mmutilsTextures.get(mc.player.getUniqueID());
                if (fakeSkin != null) {
                    mc.getTextureManager().deleteTexture(fakeSkin);
                }
            }

            // 4. Сбрасываем кэш состояний для возможности повторного включения функции
            mmutilsTextures.clear();
            originalTextures.clear();
            originalSkinType = null;
            lastAppliedModel = null;

        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    // ── Подмена ника в скорборде ─────────────────────────────────────────────

    private void replaceScoreboardNames() {
        try {
            Scoreboard scoreboard = mc.world.getScoreboard();
            Collection<ScorePlayerTeam> teams = scoreboard.getTeams();

            for (ScorePlayerTeam team : teams) {
                String prefix = (String) prefixField.get(team);
                String suffix = (String) suffixField.get(team);

                if (containsIgnoringColors(prefix, realName)) {
                    String newPrefix = replaceIgnoringColors(prefix, realName, fakeName);
                    prefixField.set(team, newPrefix);
                }

                if (containsIgnoringColors(suffix, realName)) {
                    String newSuffix = replaceIgnoringColors(suffix, realName, fakeName);
                    suffixField.set(team, newSuffix);
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
    }

    private boolean containsIgnoringColors(String text, String search) {
        if (text == null || search == null) return false;
        String clean = text.replaceAll("§.", "");
        return clean.contains(search);
    }

    private String replaceIgnoringColors(String text, String search, String replacement) {
        if (text == null || search == null || replacement == null) return text;
        if (!containsIgnoringColors(text, search)) return text;
        return text.replace(search, replacement);
    }
}