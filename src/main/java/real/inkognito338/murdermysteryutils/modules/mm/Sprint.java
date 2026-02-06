package real.inkognito338.murdermysteryutils.modules.mm;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import real.inkognito338.murdermysteryutils.modules.Module;
import real.inkognito338.murdermysteryutils.modules.settings.Setting;
import real.inkognito338.murdermysteryutils.modules.settings.SettingType;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("SpellCheckingInspection")
@SideOnly(Side.CLIENT)
public class Sprint extends Module {
    private Minecraft mc;

    public Sprint() {
        super("Sprint");

        // Режимы спринта
        String[] modes = {"Forward", "AllDirections", "Smart", "Always"};
        this.addSetting(new Setting("Mode", SettingType.MODE, "Forward", modes));

        // Основные настройки
        this.addSetting(new Setting("KeepSprint", SettingType.BOOLEAN, true));

        // Настройки отключения
        this.addSetting(new Setting("StopInWater", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("StopInLava", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("StopWhenHungry", SettingType.BOOLEAN, false));
        this.addSetting(new Setting("HungerLimit", SettingType.NUMBER, 3.0, 1.0, 20.0));
        this.addSetting(new Setting("StopWhenBlocked", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("StopWhenSneaking", SettingType.BOOLEAN, true));
        this.addSetting(new Setting("StopWhenElytra", SettingType.BOOLEAN, true));

        // Дополнительные функции
        this.addSetting(new Setting("SprintBoost", SettingType.NUMBER, 1.0, 1.0, 1.15));
        this.addSetting(new Setting("CombatMode", SettingType.BOOLEAN, false));

    }

    @Override
    public void onEnable() {
        MinecraftForge.EVENT_BUS.register(this);
        mc = Minecraft.getMinecraft();
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.world == null) return;



        String mode = (String) getSettingByName("Mode").getValue();
        boolean keepSprint = (boolean) getSettingByName("KeepSprint").getValue();
        boolean stopInWater = (boolean) getSettingByName("StopInWater").getValue();
        boolean stopInLava = (boolean) getSettingByName("StopInLava").getValue();
        boolean stopWhenHungry = (boolean) getSettingByName("StopWhenHungry").getValue();
        double hungerLimit = (double) getSettingByName("HungerLimit").getValue();
        boolean stopWhenBlocked = (boolean) getSettingByName("StopWhenBlocked").getValue();
        boolean stopWhenSneaking = (boolean) getSettingByName("StopWhenSneaking").getValue();
        boolean stopWhenElytra = (boolean) getSettingByName("StopWhenElytra").getValue();
        double sprintBoost = (double) getSettingByName("SprintBoost").getValue();
        boolean combatMode = (boolean) getSettingByName("CombatMode").getValue();

        // Форс спринта в креативном полёте
        if (mc.player.capabilities.isFlying) {
            mc.player.setSprinting(true);

            // Увеличиваем скорость полёта
            float baseFlySpeed = 0.05F; // стандартная скорость
            mc.player.capabilities.setFlySpeed(baseFlySpeed * (float) sprintBoost);

            return; // больше ничего не делаем
        }

        // Проверяем можно ли спринтовать
        if (!canSprint(stopInWater, stopInLava, stopWhenHungry, hungerLimit, stopWhenBlocked, stopWhenSneaking, stopWhenElytra)) {
            if (!keepSprint) {
                mc.player.setSprinting(false);
            }
            return;
        }

        // Применяем буст скорости если нужно
        if (sprintBoost > 1.0 && mc.player.isSprinting()) {
            applySprintBoost(sprintBoost);
        }

        // Обработка режимов спринта
        switch (mode) {
            case "Forward":
                handleForwardSprint(keepSprint, combatMode);
                break;
            case "AllDirections":
                handleAllDirectionsSprint(keepSprint, combatMode);
                break;
            case "Smart":
                handleSmartSprint(keepSprint, combatMode);
                break;
            case "Always":
                handleAlwaysSprint();
                break;
        }
    }



    private void handleForwardSprint(boolean keepSprint, boolean combatMode) {
        // Спринт только вперед
        boolean shouldSprint = mc.player.moveForward > 0;

        // В комбат режиме спринтуем даже при страфе
        if (combatMode && isInCombat() && (mc.player.moveForward != 0 || mc.player.moveStrafing != 0)) {
            shouldSprint = true;
        }

        if (shouldSprint) {
            mc.player.setSprinting(true);
        } else if (!keepSprint) {
            mc.player.setSprinting(false);
        }
    }

    private void handleAllDirectionsSprint(boolean keepSprint, boolean combatMode) {
        // Спринт в любом направлении
        boolean shouldSprint = mc.player.moveForward != 0 || mc.player.moveStrafing != 0;

        if (shouldSprint) {
            mc.player.setSprinting(true);
        } else if (!keepSprint) {
            mc.player.setSprinting(false);
        }
    }

    private void handleSmartSprint(boolean keepSprint, boolean combatMode) {
        // Умный спринт - только когда есть куда бежать
        boolean shouldSprint = mc.player.moveForward > 0 && !mc.player.collidedHorizontally;

        // В комбат режиме игнорируем проверку на препятствия
        if (combatMode && isInCombat() && (mc.player.moveForward != 0 || mc.player.moveStrafing != 0)) {
            shouldSprint = true;
        }

        if (shouldSprint) {
            mc.player.setSprinting(true);
        } else if (!keepSprint) {
            mc.player.setSprinting(false);
        }
    }

    private void handleAlwaysSprint() {
        // Просто всегда включаем спринт
        mc.player.setSprinting(true);
    }

    private boolean canSprint(boolean stopInWater, boolean stopInLava, boolean stopWhenHungry,
                              double hungerLimit, boolean stopWhenBlocked, boolean stopWhenSneaking,
                              boolean stopWhenElytra) {
        // Проверка жидкости
        if (stopInWater && mc.player.isInWater()) return false;
        if (stopInLava && mc.player.isInLava()) return false;

        // Проверка голода
        if (stopWhenHungry && mc.player.getFoodStats().getFoodLevel() <= hungerLimit) return false;

        // Проверка препятствий (только для Smart режима)
        if (stopWhenBlocked && mc.player.collidedHorizontally) return false;

        // Проверка крадущегося режима
        if (stopWhenSneaking && mc.player.isSneaking()) return false;

        // Проверка полета на элитрах
        return !stopWhenElytra || !mc.player.isElytraFlying();
    }

    private void applySprintBoost(double boost) {
        if (mc.player.moveForward != 0 || mc.player.moveStrafing != 0) {
            double currentSpeed = Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
            double maxBaseSpeed = 0.2873;

            // Применяем буст только если уже бежим с достаточной скоростью
            if (currentSpeed > maxBaseSpeed * 0.7) {
                double multiplier = 1.0 + (boost - 1.0) * 0.1; // Небольшой буст
                mc.player.motionX *= multiplier;
                mc.player.motionZ *= multiplier;

                // Ограничиваем максимальную скорость
                double newSpeed = Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
                if (newSpeed > maxBaseSpeed * 1.1) {
                    double limit = maxBaseSpeed * 1.1 / newSpeed;
                    mc.player.motionX *= limit;
                    mc.player.motionZ *= limit;
                }
            }
        }
    }

    private boolean isInCombat() {
        // Простая проверка на комбат - игрок держит меч
        return mc.player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemSword ||
                mc.player.getHeldItemOffhand().getItem() instanceof net.minecraft.item.ItemSword ||
                mc.player.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemAxe ||
                mc.player.getHeldItemOffhand().getItem() instanceof net.minecraft.item.ItemAxe;
    }

    // Важно для PvP - не сбрасывать спринт при ударе
    public boolean shouldKeepSprint() {
        return isToggled() && (boolean) getSettingByName("KeepSprint").getValue();
    }

    public boolean isCombatModeEnabled() {
        return isToggled() && (boolean) getSettingByName("CombatMode").getValue();
    }
}