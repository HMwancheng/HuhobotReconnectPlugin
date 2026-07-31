package com.huhobot.reconnect;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制台日志拦截器，平台无关（纯 java.util.logging）
 */
public class ConsoleCapture extends Handler {

    private final ReconnectPlatform platform;
    private final ReconnectManager manager;

    private static final Pattern DISCONNECT_PATTERN = Pattern.compile(
            "连接已断开|连接失败|连接超时|服务端命令断开连接"
    );

    private static final Pattern HANDSHAKE_SUCCESS_PATTERN = Pattern.compile(
            "与服务端握手成功|握手完成!"
    );

    private static final Pattern BAN_WITH_TIME_PATTERN = Pattern.compile(
            "服务器被封禁.*于 (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) 解封"
    );

    private static final Pattern BAN_PATTERN = Pattern.compile(
            "频繁连接导致的服务器被封禁"
    );

    private static final Pattern ALREADY_CONNECTED_PATTERN = Pattern.compile(
            "重连机器人失败：已在连接状态"
    );

    private static final Pattern RECONNECT_SUCCESS_PATTERN = Pattern.compile(
            "重连机器人成功"
    );

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ConsoleCapture(ReconnectPlatform platform, ReconnectManager manager) {
        this.platform = platform;
        this.manager = manager;
    }

    @Override
    public void publish(LogRecord record) {
        if (!platform.isActive()) return;

        String message = record.getMessage();
        if (message == null) return;

        boolean isHuHoBot = record.getLoggerName() != null
                && record.getLoggerName().contains("HuHoBot");

        // 检测握手成功
        if (isHuHoBot && HANDSHAKE_SUCCESS_PATTERN.matcher(message).find()) {
            runOnMainThread(manager::onHandshakeSuccess);
            return;
        }

        // 检测封禁
        if (isHuHoBot) {
            Matcher banTimeMatcher = BAN_WITH_TIME_PATTERN.matcher(message);
            if (banTimeMatcher.find()) {
                String timeStr = banTimeMatcher.group(1);
                runOnMainThread(() -> {
                    try {
                        LocalDateTime unbanTime = LocalDateTime.parse(timeStr, TIME_FORMATTER);
                        manager.onBanned(unbanTime);
                    } catch (Exception ignored) {
                        manager.onBanned(null);
                    }
                });
                return;
            }

            if (BAN_PATTERN.matcher(message).find()) {
                runOnMainThread(() -> manager.onBanned(null));
                return;
            }
        }

        // 检测重连命令响应
        if (ALREADY_CONNECTED_PATTERN.matcher(message).find()) {
            runOnMainThread(manager::onAlreadyConnected);
            return;
        }

        if (RECONNECT_SUCCESS_PATTERN.matcher(message).find()) {
            runOnMainThread(manager::onReconnectSuccess);
            return;
        }

        // 检测断开连接
        if (isHuHoBot && DISCONNECT_PATTERN.matcher(message).find()) {
            runOnMainThread(manager::onDisconnected);
        }
    }

    private void runOnMainThread(Runnable task) {
        platform.runOnMainThread(task);
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}