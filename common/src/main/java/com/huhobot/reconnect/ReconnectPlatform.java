package com.huhobot.reconnect;

import java.time.LocalDateTime;

/**
 * 平台抽象接口，由 Bukkit/Velocity 各自实现
 */
public interface ReconnectPlatform {

    /** 在主线程执行任务 */
    void runOnMainThread(Runnable task);

    /** 延迟执行任务（ticks），返回取消用的标识 */
    void scheduleDelayed(Runnable task, long delayTicks);

    /** 取消当前延迟任务 */
    void cancelDelayedTask();

    /** 定时重复任务（ticks） */
    void scheduleRepeating(Runnable task, long delayTicks, long periodTicks);

    /** 取消定时任务 */
    void cancelRepeatingTask();

    /** 执行控制台命令 */
    void dispatchCommand(String command);

    /** 日志输出 */
    void info(String message);
    void warning(String message);

    /** 配置读取 */
    boolean isActive();
    int getReconnectDelay();
    int getMaxAttempts();
    int getBanWaitTime();
    int getHealthCheckInterval();
}