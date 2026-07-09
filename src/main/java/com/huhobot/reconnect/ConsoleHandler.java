package com.huhobot.reconnect;

import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsoleHandler extends Handler {

    private final HuhobotReconnectPlugin plugin;

    // 断开连接
    private static final Pattern DISCONNECT_PATTERN = Pattern.compile(
            "\\[HuHoBot] (连接已断开|连接失败|连接超时)"
    );

    // 握手成功
    private static final Pattern HANDSHAKE_SUCCESS_PATTERN = Pattern.compile(
            "\\[HuHoBot] 与服务端握手成功"
    );

    // 封禁 + 解封时间: "服务器被封禁.*于 2026-07-03 20:46:24 解封"
    private static final Pattern BAN_WITH_TIME_PATTERN = Pattern.compile(
            "服务器被封禁.*于 (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) 解封"
    );

    // 封禁（无解封时间）
    private static final Pattern BAN_PATTERN = Pattern.compile(
            "频繁连接导致的服务器被封禁"
    );

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ConsoleHandler(HuhobotReconnectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void publish(LogRecord record) {
        if (!plugin.isEnabled()) return;

        String message = record.getMessage();
        if (message == null) return;

        // 检测握手成功
        if (HANDSHAKE_SUCCESS_PATTERN.matcher(message).find()) {
            runOnMainThread(plugin::onHandshakeSuccess);
            return;
        }

        // 检测封禁（含解封时间）
        Matcher banTimeMatcher = BAN_WITH_TIME_PATTERN.matcher(message);
        if (banTimeMatcher.find()) {
            String timeStr = banTimeMatcher.group(1);
            runOnMainThread(() -> {
                try {
                    LocalDateTime unbanTime = LocalDateTime.parse(timeStr, TIME_FORMATTER);
                    plugin.onBanned(unbanTime);
                } catch (Exception ignored) {
                    plugin.onBanned(null);
                }
            });
            return;
        }

        // 检测封禁（无解封时间）
        if (BAN_PATTERN.matcher(message).find()) {
            runOnMainThread(() -> plugin.onBanned(null));
            return;
        }

        // 检测断开连接
        if (DISCONNECT_PATTERN.matcher(message).find()) {
            runOnMainThread(plugin::onDisconnected);
        }
    }

    private void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}