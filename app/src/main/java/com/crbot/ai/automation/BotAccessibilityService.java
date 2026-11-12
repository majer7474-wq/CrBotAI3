package com.crbot.ai.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class BotAccessibilityService extends AccessibilityService {

    private static BotAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        instance = null;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static void tap(float normalizedX, float normalizedY) {
        if (instance == null) return;

        float x = normalizedX * instance.getResources()
            .getDisplayMetrics().widthPixels;
        float y = normalizedY * instance.getResources()
            .getDisplayMetrics().heightPixels;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription gesture =
            new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(
                    path, 0, 80
                ))
                .build();

        instance.dispatchGesture(gesture, null, null);
    }
}
