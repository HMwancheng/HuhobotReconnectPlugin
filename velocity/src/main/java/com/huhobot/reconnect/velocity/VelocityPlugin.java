package com.huhobot.reconnect.velocity;

import com.google.inject.Inject;
import com.huhobot.reconnect.ConsoleCapture;
import com.huhobot.reconnect.ReconnectManager;
import com.huhobot.reconnect.ReconnectPlatform;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "huhobotreconnect",
        name = "HuhobotReconnect",
        version = "1.0.0",
        authors = {"HuhobotReconnect"},
        description = "Auto reconnect HuHoBot when disconnected"
)
public class VelocityPlugin implements ReconnectPlatform {

    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private final ProxyServer server;
    private final Path dataDirectory;
    private final java.util.logging.Logger logger;

    private ConsoleCapture consoleCapture;
    private ReconnectManager manager;
    private ScheduledTask delayedTask;
    private ScheduledTask repeatingTask;

    private boolean enabled;
    private int reconnectDelay;
    private int maxAttempts;
    private int banWaitTime;
    private int healthCheckInterval;

    @Inject
    public VelocityPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.logger = java.util.logging.Logger.getLogger("HuhobotReconnect");
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        saveDefaultConfig();

        manager = new ReconnectManager(this);
        consoleCapture = new ConsoleCapture(this, manager);
        ROOT_LOGGER.addHandler(consoleCapture);

        server.getCommandManager().register("huhobotreconnect", new HrcCommand(), "hrc");

        manager.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        ROOT_LOGGER.removeHandler(consoleCapture);
        manager.stop();
        cancelDelayedTask();
        cancelRepeatingTask();
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) return;

        try (InputStream in = Files.newInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(in);
            if (data == null) return;

            reconnectDelay = getInt(data, "reconnect_delay", 15);
            maxAttempts = getInt(data, "max_attempts", 0);
            banWaitTime = getInt(data, "ban_wait_time", 600);
            healthCheckInterval = getInt(data, "health_check_interval", 300);
            enabled = getBoolean(data, "enabled", true);
        } catch (IOException e) {
            logger.warning("读取配置失败: " + e.getMessage());
        }
    }

    private void saveDefaultConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.yml");
            if (Files.exists(configFile)) return;

            String defaultConfig = "# 检测到断开后等待多少秒再重连\n"
                    + "reconnect_delay: 15\n\n"
                    + "# 定期健康检查间隔（秒），0=禁用\n"
                    + "health_check_interval: 300\n\n"
                    + "# 最大重连次数，0表示无限重连\n"
                    + "max_attempts: 0\n\n"
                    + "# 当无法解析封禁解封时间时的默认等待时间（秒）\n"
                    + "ban_wait_time: 600\n\n"
                    + "# 是否启用自动重连\n"
                    + "enabled: true\n";
            Files.writeString(configFile, defaultConfig);
        } catch (IOException e) {
            logger.warning("保存默认配置失败: " + e.getMessage());
        }
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    // ==================== ReconnectPlatform 实现 ====================

    @Override
    public void runOnMainThread(Runnable task) {
        server.getScheduler().buildTask(this, task).schedule();
    }

    @Override
    public void scheduleDelayed(Runnable task, long delayTicks) {
        cancelDelayedTask();
        delayedTask = server.getScheduler().buildTask(this, task)
                .delay(delayTicks * 50, TimeUnit.MILLISECONDS)
                .schedule();
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
        repeatingTask = server.getScheduler().buildTask(this, task)
                .delay(delayTicks * 50, TimeUnit.MILLISECONDS)
                .repeat(periodTicks * 50, TimeUnit.MILLISECONDS)
                .schedule();
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
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public int getReconnectDelay() { return reconnectDelay; }
    @Override public int getMaxAttempts() { return maxAttempts; }
    @Override public int getBanWaitTime() { return banWaitTime; }
    @Override public int getHealthCheckInterval() { return healthCheckInterval; }

    // ==================== 命令 ====================

    private class HrcCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                loadConfig();
                source.sendMessage(net.kyori.adventure.text.Component.text("[HuHoBotReconnect] 配置已重载"));
                info("配置已重载: reconnect_delay=" + reconnectDelay
                        + ", health_check=" + healthCheckInterval
                        + ", max_attempts=" + maxAttempts
                        + ", ban_wait_time=" + banWaitTime
                        + ", enabled=" + enabled);
                return;
            }

            source.sendMessage(net.kyori.adventure.text.Component.text(
                    "[HuHoBotReconnect] 状态:"
                            + " enabled=" + enabled
                            + ", reconnect_delay=" + reconnectDelay + "s"
                            + ", health_check=" + healthCheckInterval + "s"
                            + ", max_attempts=" + maxAttempts
                            + ", ban_wait_time=" + banWaitTime + "s"
                            + ", " + manager.getStatus()));
        }
    }
}