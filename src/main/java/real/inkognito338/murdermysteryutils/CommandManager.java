package real.inkognito338.murdermysteryutils;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("SpellCheckingInspection")
public class CommandManager {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Слушатель чата
    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(".")) return; // только команды
        event.setCanceled(true); // не отправляем на сервер
        executeCommand(message);
    }

    // Обработка команды
    public void executeCommand(String input) {
        String[] args = input.substring(1).split(" "); // убираем точку
        if (args.length == 0) return;

        switch (args[0].toLowerCase()) {
            case "gm":
                if (args.length < 2) {
                    sendMessage("§cИспользование: .gm <0/1/2/3>");
                    return;
                }
                try {
                    int gm = Integer.parseInt(args[1]);
                    setVisualGamemode(gm);
                } catch (NumberFormatException e) {
                    sendMessage("§cНеверный номер режима!");
                }
                break;

            case "dumpentities":
                dumpEntitiesToFile();
                break;

            case "entities":
            case "ents":
                showEntities();
                break;

            case "pos":
                copyPositionToClipboard();
                break;

            default:
                sendMessage("§cНеизвестная команда! Доступные: .gm, .dumpentities, .entities, .pos");
        }
    }

    // Копировать координаты в буфер обмена
    private void copyPositionToClipboard() {
        if (mc.player == null) {
            sendMessage("§cИгрок не загружен!");
            return;
        }

        try {
            // Получаем точные координаты
            double x = mc.player.posX;
            double y = mc.player.posY;
            double z = mc.player.posZ;
            float yaw = mc.player.rotationYaw;
            float pitch = mc.player.rotationPitch;

            // Форматируем координаты с высокой точностью
            String coordinates = String.format(Locale.US, "%.6f, %.6f, %.6f", x, y, z);
            String coordinatesWithYawPitch = String.format(Locale.US, "%.6f, %.6f, %.6f, %.1f, %.1f", x, y, z, yaw, pitch);

            // Копируем в буфер обмена
            StringSelection selection = new StringSelection(coordinates);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);

            sendMessage("§aКоординаты скопированы в буфер обмена!");
            sendMessage("§7Координаты: §e" + coordinates);
            sendMessage("§7С поворотом: §e" + coordinatesWithYawPitch);
            sendMessage("§7Формат: §eX, Y, Z");

        } catch (Exception e) {
            sendMessage("§cОшибка при копировании в буфер обмена: " + e.getMessage());
        }
    }

    // Дамп ВСЕХ ентити в файл (отсортировано по расстоянию)
    private void dumpEntitiesToFile() {
        if (mc.world == null || mc.player == null) {
            sendMessage("§cМир не загружен!");
            return;
        }

        try {
            // Создаем папку если нет
            File modDir = new File(mc.mcDataDir, "MurderMysteryUtils");
            if (!modDir.exists()) {
                modDir.mkdirs();
            }

            // Создаем файл с timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File outputFile = new File(modDir, "entities_dump_" + timestamp + ".txt");

            // Получаем и сортируем ВСЕ ентити по расстоянию
            List<Entity> entities = getSortedEntitiesByDistance();

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write("=== Entity Dump (Sorted by Distance) ===\n");
                writer.write("Time: " + new Date() + "\n");
                writer.write("World: " + mc.world.getWorldInfo().getWorldName() + "\n");
                writer.write(String.format("Player Position: [%.1f, %.1f, %.1f]\n",
                        mc.player.posX, mc.player.posY, mc.player.posZ));
                writer.write("Total entities: " + entities.size() + "\n\n");

                // Записываем информацию о КАЖДОЙ ентити
                for (int i = 0; i < entities.size(); i++) {
                    Entity entity = entities.get(i);
                    double distance = getDistanceToPlayer(entity);
                    writer.write(String.format("[%d] Distance: %.1f | %s\n",
                            i + 1, distance, getEntityInfo(entity)));
                }

                writer.write("\n=== End of Dump ===");
            }

            sendMessage("§aДамп ВСЕХ ентити сохранен в: §e" + outputFile.getName());
            sendMessage("§aВсего ентити: §e" + entities.size() + " §a(отсортировано по расстоянию)");

        } catch (IOException e) {
            sendMessage("§cОшибка при сохранении файла: " + e.getMessage());
        }
    }

    // Получить ВСЕ ентити отсортированные по расстоянию
    private List<Entity> getSortedEntitiesByDistance() {
        List<Entity> entities = new ArrayList<>(mc.world.loadedEntityList);

        // Убираем самого игрока из списка если нужно
        entities.remove(mc.player);

        // Сортируем по расстоянию до игрока
        Collections.sort(entities, new Comparator<Entity>() {
            @Override
            public int compare(Entity e1, Entity e2) {
                double dist1 = getDistanceToPlayer(e1);
                double dist2 = getDistanceToPlayer(e2);
                return Double.compare(dist1, dist2);
            }
        });

        return entities;
    }

    // Получить расстояние от ентити до игрока
    private double getDistanceToPlayer(Entity entity) {
        if (mc.player == null) return Double.MAX_VALUE;
        return mc.player.getDistance(entity);
    }

    // Показать ближайшие ентити в чате
    private void showEntities() {
        if (mc.world == null || mc.player == null) {
            sendMessage("§cМир не загружен!");
            return;
        }

        List<Entity> entities = getSortedEntitiesByDistance();
        sendMessage("§6=== Ближайшие ентити (§e" + entities.size() + "§6) ===");

        // Показываем первые 20 ближайших
        int showCount = Math.min(entities.size(), 20);
        for (int i = 0; i < showCount; i++) {
            Entity entity = entities.get(i);
            double distance = getDistanceToPlayer(entity);
            sendMessage("§7" + (i + 1) + ". §f" + getShortEntityInfo(entity, distance));
        }

        if (entities.size() > 20) {
            sendMessage("§7... и еще §e" + (entities.size() - 20) + "§7 ентити");
            sendMessage("§7Используйте §e.dumpentities §7для полного дампа ВСЕХ ентити");
        }
    }

    // Полная информация о ентити для файла
    private String getEntityInfo(Entity entity) {
        String customName = entity.getCustomNameTag();
        return String.format(
                "Type: %s, Name: %s, ID: %d, Pos: [%.2f, %.2f, %.2f], UUID: %s, Dead: %s, CustomName: %s",
                entity.getClass().getSimpleName(),
                entity.getName(),
                entity.getEntityId(),
                entity.posX, entity.posY, entity.posZ,
                entity.getUniqueID(),
                entity.isDead,
                customName.isEmpty() ? "N/A" : customName
        );
    }

    // Краткая информация о ентити для чата (с расстоянием)
    private String getShortEntityInfo(Entity entity, double distance) {
        return String.format(
                "§e%s§7 - §f%s§7 [§a%.1f§7 blocks] at [§6%.1f, %.1f, %.1f§7]",
                entity.getClass().getSimpleName(),
                entity.getName(),
                distance,
                entity.posX, entity.posY, entity.posZ
        );
    }

    // Визуальное изменение гейммода (только клиент)
    private void setVisualGamemode(int gm) {
        if (mc.player == null || mc.playerController == null) return;

        GameType gameType;
        switch (gm) {
            case 0:
                gameType = GameType.SURVIVAL;
                break;
            case 1:
                gameType = GameType.CREATIVE;
                break;
            case 2:
                gameType = GameType.ADVENTURE;
                break;
            case 3:
                gameType = GameType.SPECTATOR;
                break;
            default:
                sendMessage("§cНеверный режим! Используйте 0, 1, 2 или 3.");
                return;
        }

        mc.playerController.setGameType(gameType);
        sendMessage("§aВизуальный режим установлен: §e" + gameType.getName());
    }

    // Отправка сообщения в чат
    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§7[§5MurderMystery Utils§7]§f " + message));
        }
    }
}