package com.huhobot.reconnect.bukkit;

import com.huhobot.reconnect.ConsoleCapture;
import com.huhobot.reconnect.ReconnectManager;
import com.huhobot.reconnect.ReconnectPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public class BukkitPlugin extends JavaPlugin implements ReconnectPlatform {

    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private ConsoleCapture consoleCapture;
    private ReconnectManager manager;
    private BukkitTask delayedTask;
    private BukkitTask repeatingTask;

    private boolean enabled;
    private int reconnectDelay;
    private int maxAttempts;
    private int banWaitTime;
    private int healthCheckInterval;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        manager = new ReconnectManager(this);
        consoleCapture = new ConsoleCapture(this, manager);
        ROOT_LOGGER.addHandler(consoleCapture);

        manager.start();
    }

    @Override
    public void onDisable() {
        ROOT_LOGGER.removeHandler(consoleCapture);
        manager.stop();
        cancelDelayedTask();
        cancelRepeatingTask();
    }

    private void loadConfig() {
        reloadConfig();
        reconnectDelay = getConfig().getInt("reconnect_delay", 15);
        maxAttempts = getConfig().getInt("max_attempts", 0);
        banWaitTime = getConfig().getInt("ban_wait_time", 600);
        healthCheckInterval = getConfig().getInt("health_check_interval", 300);
        enabled = getConfig().getBoolean("enabled", true);
    }

    // ==================== ReconnectPlatform 实现 ====================

    @Override
    public void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(this, task);
    }

    @Override
    public void scheduleDelayed(Runnable task, long delayTicks) {
        cancelDelayedTask();
        delayedTask = Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
    }

    @Override
    public void cancelDelayedTask() {
        if (delayedTask != null) {
            delayedTask.cancel();
            delayedTask = null;
        }
    }

    @Override
    public void scheduleRepeating(Runnable task, long delayTicks, long periodTicks) {
        cancelRepeatingTask();
        repeatingTask = Bukkit.getScheduler().runTaskTimer(this, task, delayTicks, periodTicks);
    }

    @Override
    public void cancelRepeatingTask() {
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }

    @Override
    public void dispatchCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public void info(String message) {
        getLogger().info(message);
    }

    @Override
    public void warning(String message) {
        getLogger().warning(message);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public int getReconnectDelay() { return reconnectDelay; }
    @Override public int getMaxAttempts() { return maxAttempts; }
    @Override public int getBanWaitTime() { return banWaitTime; }
    @Override public int getHealthCheckInterval() { return healthCheckInterval; }

    // ==================== 命令处理 ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            loadConfig();
            sender.sendMessage("[HuHoBotReconnect] 配置已重载");
            info("配置已重载: reconnect_delay=" + reconnectDelay
                    + ", health_check=" + healthCheckInterval
                    + ", max_attempts=" + maxAttempts
                    + ", ban_wait_time=" + banWaitTime
                    + ", enabled=" + enabled);
            return true;
        }

        sender.sendMessage("[HuHoBotReconnect] 状态:"
                + " enabled=" + enabled
                + ", reconnect_delay=" + reconnectDelay + "s"
                + ", health_check=" + healthCheckInterval + "s"
                + ", max_attempts=" + maxAttempts
                + ", ban_wait_time=" + banWaitTime + "s"
                + ", " + manager.getStatus());
        return true;
    }
}