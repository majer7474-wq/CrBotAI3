package com.crbot.ai.engine;

import com.crbot.ai.game.GameState;

public class DecisionEngine {

    public enum ActionType {
        WAIT,
        DEFEND,
        ATTACK
    }

    public static class Decision {
        public ActionType type;
        public int cardIndex;
        public float x;
        public float y;

        public Decision(ActionType type, int cardIndex, float x, float y) {
            this.type = type;
            this.cardIndex = cardIndex;
            this.x = x;
            this.y = y;
        }
    }

    public Decision decide(GameState state) {

        // Ne donosimo poteze na temelju neprovjerenih podataka.
        if (state == null || !state.visionReliable) {
            return waitDecision();
        }

        if (state.ownElixir < 3f) {
            return waitDecision();
        }

        if (state.enemyThreatDetected) {
            return new Decision(
                ActionType.DEFEND, -1, 0.5f, 0.65f
            );
        }

        return new Decision(
            ActionType.ATTACK, -1, 0.5f, 0.35f
        );
    }

    private Decision waitDecision() {
        return new Decision(ActionType.WAIT, -1, 0f, 0f);
    }
}
