package com.huhobot.reconnect;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.logging.Logger;

public class HuhobotReconnectPlugin extends JavaPlugin {

    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private ConsoleHandler consoleHandler;
    private BukkitTask reconnectTask;
    private BukkitTask healthCheckTask;
    private int reconnectAttempts;
    private boolean enabled;

    private int reconnectDelay;
    private int maxAttempts;
    private int banWaitTime;
    private int healthCheckInterval;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        consoleHandler = new ConsoleHandler(this);
        ROOT_LOGGER.addHandler(consoleHandler);

        startHealthCheck();

        getLogger().info("HuHoBot重连插件已启用 (reconnect_delay=" + reconnectDelay
                + "s, health_check=" + healthCheckInterval + "s)");
    }

    @Override
    public void onDisable() {
        ROOT_LOGGER.removeHandler(consoleHandler);
        cancelReconnectTask();
        stopHealthCheck();
        getLogger().info("HuHoBot重连插件已禁用");
    }

    private void loadConfig() {
        reloadConfig();
        reconnectDelay = getConfig().getInt("reconnect_delay", 15);
        maxAttempts = getConfig().getInt("max_attempts", 0);
        banWaitTime = getConfig().getInt("ban_wait_time", 600);
        healthCheckInterval = getConfig().getInt("health_check_interval", 300);
        enabled = getConfig().getBoolean("enabled", true);
    }

    // ==================== 回调方法（由ConsoleHandler调用，已在主线程） ====================

    void onDisconnected() {
        if (!enabled) return;

        // 如果已有重连任务在等待，不重复创建
        if (reconnectTask != null) return;

        getLogger().info("检测到HuHoBot断开连接，将在 " + reconnectDelay + " 秒后尝试重连");
        scheduleReconnect();
    }

    void onHandshakeSuccess() {
        if (reconnectTask != null) {
            getLogger().info("检测到HuHoBot握手成功，取消重连任务");
            cancelReconnectTask();
        }
        reconnectAttempts = 0;
    }

    void onBanned(LocalDateTime unbanTime) {
        getLogger().warning("检测到HuHoBot被封禁！");
        cancelReconnectTask();

        long waitSeconds;
        if (unbanTime != null) {
            waitSeconds = Duration.between(LocalDateTime.now(), unbanTime).getSeconds();
            if (waitSeconds < 0) waitSeconds = 0;
            getLogger().info("解封时间: " + unbanTime + "，将在 " + waitSeconds + " 秒后重连");
        } else {
            waitSeconds = banWaitTime;
            getLogger().info("无法解析解封时间，使用默认等待时间: " + waitSeconds + " 秒");
        }

        long ticks = waitSeconds * 20L;
        reconnectTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            reconnectTask = null;
            getLogger().info("封禁等待结束，开始重连...");
            doReconnect();
        }, ticks);
    }

    // 健康检查/重连命令响应：已在连接状态
    void onAlreadyConnected() {
        reconnectAttempts = 0;
        if (reconnectTask != null) {
            getLogger().info("HuHoBot已在连接状态，取消重连任务");
            cancelReconnectTask();
        }
    }

    // 健康检查/重连命令响应：重连成功
    void onReconnectSuccess() {
        reconnectAttempts = 0;
        getLogger().info("HuHoBot重连成功");
    }

    // ==================== 重连调度 ====================

    private void scheduleReconnect() {
        long ticks = reconnectDelay * 20L;
        reconnectTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            reconnectTask = null;
            doReconnect();
        }, ticks);
    }

    private void doReconnect() {
        if (!enabled) return;

        if (maxAttempts > 0 && reconnectAttempts >= maxAttempts) {
            getLogger().warning("已达到最大重连次数 (" + maxAttempts + ")，停止重连");
            return;
        }

        reconnectAttempts++;
        getLogger().info("正在执行重连 (第" + reconnectAttempts + "次"
                + (maxAttempts > 0 ? "/" + maxAttempts : "") + ")...");

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "huhobot reconnect");
    }

    private void cancelReconnectTask() {
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
    }

    // ==================== 健康检查 ====================

    private void startHealthCheck() {
        if (healthCheckInterval <= 0) return;

        stopHealthCheck();
        long ticks = healthCheckInterval * 20L;
        healthCheckTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!enabled || reconnectTask != null) return;
            getLogger().info("定期健康检查：执行重连检测...");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "huhobot reconnect");
        }, ticks, ticks);
    }

    private void stopHealthCheck() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }
    }

    // ==================== 命令处理 ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            loadConfig();
            stopHealthCheck();
            startHealthCheck();
            sender.sendMessage("[HuHoBotReconnect] 配置已重载");
            getLogger().info("配置已重载: reconnect_delay=" + reconnectDelay
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
                + ", reconnect_attempts=" + reconnectAttempts
                + ", pending_reconnect=" + (reconnectTask != null)
                + ", health_check_active=" + (healthCheckTask != null));
        return true;
    }
}