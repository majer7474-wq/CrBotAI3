package com.crbot.ai.engine;

import com.crbot.ai.game.CardCatalog;
import com.crbot.ai.game.GameState;

public class DecisionEngine {

    public enum ActionType {
        WAIT,
        PLAY_CARD
    }

    public static class Decision {
        public final ActionType type;
        public final int cardIndex;
        public final int cardId;
        public final float x;
        public final float y;
        public final String reason;

        public Decision(ActionType type, int cardIndex, int cardId,
                        float x, float y, String reason) {
            this.type = type;
            this.cardIndex = cardIndex;
            this.cardId = cardId;
            this.x = x;
            this.y = y;
            this.reason = reason;
        }
    }

    private boolean nextHogLeft = true;

    public Decision decide(GameState state) {
        if (state == null ||
            !state.visionReliable ||
            !state.elixirDetected ||
            !state.handDetected ||
            !state.hasUsableHand() ||
            !state.isFresh() ||
            state.ownElixir < 0f ||
            state.ownElixir > 10f) {
            return waitDecision("Game state nije pouzdan.");
        }

        if (state.enemyThreatDetected &&
            state.enemyTroopDetected &&
            state.enemyTroopOnOurSide) {
            Decision defense = chooseDefense(state);
            if (defense != null) return defense;
        }

        int hog = state.findCard(CardCatalog.HOG_RIDER);
        if (hog >= 0 && state.ownElixir >= 4f) {
            float laneX = nextHogLeft ? 0.30f : 0.70f;
            nextHogLeft = !nextHogLeft;

            return play(state, hog, CardCatalog.HOG_RIDER,
                laneX, 0.535f, "Hog Rider lane attack.");
        }

        if (!state.enemyThreatDetected && state.ownElixir >= 8f) {
            int spirit = state.findCard(CardCatalog.ICE_SPIRIT);
            if (spirit >= 0 && state.ownElixir >= 1f) {
                return play(state, spirit, CardCatalog.ICE_SPIRIT,
                    0.50f, 0.62f, "Ice Spirit cycle.");
            }

            int skeletons = state.findCard(CardCatalog.SKELETONS);
            if (skeletons >= 0 && state.ownElixir >= 1f) {
                return play(state, skeletons, CardCatalog.SKELETONS,
                    0.50f, 0.63f, "Skeletons cycle.");
            }
        }

        return waitDecision("Cekanje boljeg poteza.");
    }

    private Decision chooseDefense(GameState state) {
        int musketeer = state.findCard(CardCatalog.MUSKETEER);
        if (musketeer >= 0 && state.ownElixir >= 4f) {
            float x = state.enemyTroopX < 0.5f ? 0.35f : 0.65f;
            return play(state, musketeer, CardCatalog.MUSKETEER,
                x, 0.72f, "Musketeer defense.");
        }

        int iceGolem = state.findCard(CardCatalog.ICE_GOLEM);
        if (iceGolem >= 0 && state.ownElixir >= 2f) {
            return play(state, iceGolem, CardCatalog.ICE_GOLEM,
                state.enemyTroopX, 0.75f, "Ice Golem defense.");
        }

        int spirit = state.findCard(CardCatalog.ICE_SPIRIT);
        if (spirit >= 0 && state.ownElixir >= 1f) {
            return play(state, spirit, CardCatalog.ICE_SPIRIT,
                state.enemyTroopX, 0.70f, "Ice Spirit defense.");
        }

        int skeletons = state.findCard(CardCatalog.SKELETONS);
        if (skeletons >= 0 && state.ownElixir >= 1f) {
            return play(state, skeletons, CardCatalog.SKELETONS,
                state.enemyTroopX, 0.72f, "Skeletons defense.");
        }

        return null;
    }

    private Decision play(GameState state, int index, int cardId,
                          float x, float y, String reason) {
        int cost = CardCatalog.cost(cardId);

        if (index < 0 || cost < 0 || state.ownElixir < cost) {
            return waitDecision("Nedovoljno elixira.");
        }

        return new Decision(ActionType.PLAY_CARD, index, cardId,
            x, y, reason);
    }

    private Decision waitDecision(String reason) {
        return new Decision(ActionType.WAIT, -1,
            CardCatalog.UNKNOWN, 0f, 0f, reason);
    }
}
