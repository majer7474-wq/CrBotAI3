package com.crbot.ai.automation;

import com.crbot.ai.engine.DecisionEngine;
import com.crbot.ai.game.GameState;

import java.util.Arrays;

public final class BotController {

    private static final DecisionEngine engine = new DecisionEngine();
    private static int[] lastPlayedHand = null;
    private static boolean waitingForHandChange = false;
    private static long lastPlayTime = 0L;
    private static final long PLAY_COOLDOWN_MS = 1500L;
    private static final long HAND_CHANGE_TIMEOUT_MS = 2200L;

    private BotController() {}

    private static long nowOrCurrentTime() {
        return System.currentTimeMillis();
    }

    public static synchronized void onFrame(GameState state) {
        if (!BotRuntime.isActive() ||
            !BotRuntime.isAutomationEnabled() ||
            state == null ||
            !state.handDetected ||
            !state.hasUsableHand() ||
            !BotAccessibilityService.isConnected()) {
            return;
        }

        if (waitingForHandChange) {
            if (!Arrays.equals(lastPlayedHand, state.hand)) {
                waitingForHandChange = false;
            } else if (nowOrCurrentTime() - lastPlayTime
                    < HAND_CHANGE_TIMEOUT_MS) {
                return;
            } else {
                // Igra nije registrirala potez: dopusti novi pokušaj.
                waitingForHandChange = false;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastPlayTime < PLAY_COOLDOWN_MS) return;

        DecisionEngine.Decision decision = engine.decide(state);

        if (decision.type != DecisionEngine.ActionType.PLAY_CARD) return;
        if (decision.cardIndex < 0 || decision.cardIndex > 3) return;

        boolean sent = BotAccessibilityService.playCard(
            decision.cardIndex, decision.x, decision.y
        );

        if (sent) {
            lastPlayedHand = state.hand.clone();
            waitingForHandChange = true;
            lastPlayTime = now;
        }
    }

    public static synchronized void reset() {
        lastPlayedHand = null;
        waitingForHandChange = false;
        lastPlayTime = 0L;
    }
}
