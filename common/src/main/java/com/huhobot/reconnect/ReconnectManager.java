package com.huhobot.reconnect;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 核心重连逻辑，平台无关
 */
public class ReconnectManager {

    private final ReconnectPlatform platform;
    private boolean reconnectTaskPending;
    private boolean healthCheckActive;
    private int reconnectAttempts;

    public ReconnectManager(ReconnectPlatform platform) {
        this.platform = platform;
    }

    public void start() {
        startHealthCheck();
        platform.info("重连管理器已启动 (reconnect_delay=" + platform.getReconnectDelay()
                + "s, health_check=" + platform.getHealthCheckInterval() + "s)");
    }

    public void stop() {
        cancelReconnectTask();
        stopHealthCheck();
        platform.info("重连管理器已停止");
    }

    // ==================== 回调方法 ====================

    public void onDisconnected() {
        if (!platform.isActive()) return;
        if (reconnectTaskPending) return;

        platform.info("检测到HuHoBot断开连接，将在 " + platform.getReconnectDelay() + " 秒后尝试重连");
        scheduleReconnect();
    }

    public void onHandshakeSuccess() {
        if (reconnectTaskPending) {
            platform.info("检测到HuHoBot握手成功，取消重连任务");
            cancelReconnectTask();
        }
        reconnectAttempts = 0;
    }

    public void onBanned(LocalDateTime unbanTime) {
        platform.warning("检测到HuHoBot被封禁！");
        cancelReconnectTask();

        long waitSeconds;
        if (unbanTime != null) {
            waitSeconds = Duration.between(LocalDateTime.now(), unbanTime).getSeconds();
            if (waitSeconds < 0) waitSeconds = 0;
            platform.info("解封时间: " + unbanTime + "，将在 " + waitSeconds + " 秒后重连");
        } else {
            waitSeconds = platform.getBanWaitTime();
            platform.info("无法解析解封时间，使用默认等待时间: " + waitSeconds + " 秒");
        }

        long ticks = waitSeconds * 20L;
        reconnectTaskPending = true;
        platform.scheduleDelayed(() -> {
            reconnectTaskPending = false;
            platform.info("封禁等待结束，开始重连...");
            doReconnect();
        }, ticks);
    }

    public void onAlreadyConnected() {
        reconnectAttempts = 0;
        if (reconnectTaskPending) {
            platform.info("HuHoBot已在连接状态，取消重连任务");
            cancelReconnectTask();
        }
    }

    public void onReconnectSuccess() {
        reconnectAttempts = 0;
        platform.info("HuHoBot重连成功");
    }

    // ==================== 重连调度 ====================

    private void scheduleReconnect() {
        long ticks = platform.getReconnectDelay() * 20L;
        reconnectTaskPending = true;
        platform.scheduleDelayed(() -> {
            reconnectTaskPending = false;
            doReconnect();
        }, ticks);
    }

    private void doReconnect() {
        if (!platform.isActive()) return;

        if (platform.getMaxAttempts() > 0 && reconnectAttempts >= platform.getMaxAttempts()) {
            platform.warning("已达到最大重连次数 (" + platform.getMaxAttempts() + ")，停止重连");
            return;
        }

        reconnectAttempts++;
        platform.info("正在执行重连 (第" + reconnectAttempts + "次"
                + (platform.getMaxAttempts() > 0 ? "/" + platform.getMaxAttempts() : "") + ")...");

        platform.dispatchCommand("huhobot reconnect");
    }

    private void cancelReconnectTask() {
        if (reconnectTaskPending) {
            platform.cancelDelayedTask();
            reconnectTaskPending = false;
        }
    }

    // ==================== 健康检查 ====================

    private void startHealthCheck() {
        int interval = platform.getHealthCheckInterval();
        if (interval <= 0) return;

        stopHealthCheck();
        long ticks = interval * 20L;
        healthCheckActive = true;
        platform.scheduleRepeating(() -> {
            if (!platform.isActive() || reconnectTaskPending) return;
            platform.info("定期健康检查：执行重连检测...");
            platform.dispatchCommand("huhobot reconnect");
        }, ticks, ticks);
    }

    private void stopHealthCheck() {
        if (healthCheckActive) {
            platform.cancelRepeatingTask();
            healthCheckActive = false;
        }
    }

    // ==================== 状态查询 ====================

    public String getStatus() {
        return "reconnect_attempts=" + reconnectAttempts
                + ", pending_reconnect=" + reconnectTaskPending
                + ", health_check_active=" + healthCheckActive;
    }
}