package com.crbot.ai.automation;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.CopyOnWriteArrayList;

public final class BotRuntime {

    private static final Object LOCK = new Object();
    private static final CopyOnWriteArrayList<Runnable> STOP_ACTIONS =
        new CopyOnWriteArrayList<>();

    private static volatile boolean active = false;
    private static volatile boolean automationEnabled = false;

    private BotRuntime() {}

    public static void startSession() {
        synchronized (LOCK) {
            active = true;
            automationEnabled = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isAutomationEnabled() {
        return automationEnabled;
    }

    public static void enableAutomation() {
        synchronized (LOCK) {
            if (active) {
                automationEnabled = true;
            }
        }
    }

    public static void registerStopAction(Runnable action) {
        if (action != null) {
            STOP_ACTIONS.addIfAbsent(action);
            if (!active) {
                try {
                    action.run();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void unregisterStopAction(Runnable action) {
        STOP_ACTIONS.remove(action);
    }

    public static void runIfActive(Runnable action) {
        synchronized (LOCK) {
            if (active && action != null) {
                action.run();
            }
        }
    }

    public static void runIfAutomationEnabled(Runnable action) {
        synchronized (LOCK) {
            if (active && automationEnabled && action != null) {
                action.run();
            }
        }
    }

    public static void deactivate() {
        synchronized (LOCK) {
            active = false;
            automationEnabled = false;

            for (Runnable action : STOP_ACTIONS) {
                try {
                    action.run();
                } catch (Exception ignored) {}
            }
        }
    }

    public static void stopAll(Context context) {
        deactivate();
        context.stopService(
            new Intent(context, ScreenCaptureService.class)
        );
    }
}
