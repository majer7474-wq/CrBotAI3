package com.crbot.ai.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class BotAccessibilityService extends AccessibilityService {

    private static volatile BotAccessibilityService instance;

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


    public static boolean playCard(int cardIndex, float arenaX, float arenaY) {
        if (!BotRuntime.isActive() || !BotRuntime.isAutomationEnabled()) {
            return false;
        }

        BotAccessibilityService current = instance;
        if (current == null || cardIndex < 0 || cardIndex > 3) return false;

        float[] cardX = com.crbot.ai.vision.VisionConfig.HAND_CARD_TAP_X;
        float width = current.getResources().getDisplayMetrics().widthPixels;
        float height = current.getResources().getDisplayMetrics().heightPixels;

        // Jedan kontinuirani drag od karte do arene.
        Path dragPath = new Path();
        dragPath.moveTo(cardX[cardIndex] * width, 0.89f * height);
        dragPath.lineTo(arenaX * width, arenaY * height);

        GestureDescription gesture =
            new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(dragPath, 0, 280))
                .build();

        return current.dispatchGesture(gesture, null, null);
    }

    public static void tap(float normalizedX, float normalizedY) {
        BotRuntime.runIfAutomationEnabled(() -> {
            BotAccessibilityService current = instance;
            if (current == null) return;

            float x = normalizedX * current.getResources()
                .getDisplayMetrics().widthPixels;
            float y = normalizedY * current.getResources()
                .getDisplayMetrics().heightPixels;

            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture =
                new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(
                        path, 0, 80
                    ))
                    .build();

            current.dispatchGesture(gesture, null, null);
        });
    }
}
