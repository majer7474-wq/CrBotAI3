package com.crbot.ai.engine;

import com.crbot.ai.game.CardCatalog;
import com.crbot.ai.game.GameState;

public final class DecisionEngine {

    public enum ActionType { WAIT, PLAY_CARD }

    public static final class Decision {
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

    private long lastHogTime = 0L;
    private long lastCycleTime = 0L;
    private boolean nextLeft = true;

    public synchronized Decision decide(GameState s) {
        if (!valid(s)) return waitFor("Nepouzdano stanje igre.");

        if (s.enemyThreatDetected && s.enemyTroopDetected
                && s.enemyTroopOnOurSide) {
            Decision defense = defend(s);
            if (defense != null) return defense;
        }

        int hog = s.findCard(CardCatalog.HOG_RIDER);
        long now = System.currentTimeMillis();

        // Hog se ne baca automatski čim se skupi 4 eliksira.
        if (hog >= 0 && s.ownElixir >= 7f
                && now - lastHogTime >= 3000L
                && !s.enemyThreatDetected) {
            float lane = nextLeft ? 0.30f : 0.70f;
            nextLeft = !nextLeft;
            lastHogTime = now;
            return play(s, hog, CardCatalog.HOG_RIDER, lane, 0.535f,
                    "2.6: Hog napad uz elixir rezervu.");
        }

        // Cikliranje samo kada je eliksir gotovo pun.
        if (s.ownElixir >= 9f && !s.enemyThreatDetected
                && now - lastCycleTime >= 2200L) {
            Decision cycle = cycle(s);
            if (cycle != null) {
                lastCycleTime = now;
                return cycle;
            }
        }

        return waitFor("Čuvanje obrane i čekanje boljeg poteza.");
    }

    private boolean valid(GameState s) {
        return s != null && s.visionReliable && s.elixirDetected
                && s.handDetected && s.hasUsableHand() && s.isFresh()
                && s.ownElixir >= 0f && s.ownElixir <= 10f;
    }

    private Decision defend(GameState s) {
        float enemyX = clamp(s.enemyTroopX, 0.15f, 0.85f);

        int cannon = s.findCard(CardCatalog.CANNON);
        if (cannon >= 0 && s.ownElixir >= 3f) {
            return play(s, cannon, CardCatalog.CANNON,
                    0.50f, 0.70f, "2.6: Cannon centralni pull.");
        }

        int musk = s.findCard(CardCatalog.MUSKETEER);
        if (musk >= 0 && s.ownElixir >= 4f) {
            float x = enemyX < 0.5f ? 0.67f : 0.33f;
            return play(s, musk, CardCatalog.MUSKETEER,
                    x, 0.78f, "2.6: Musketeer obrana.");
        }

        int golem = s.findCard(CardCatalog.ICE_GOLEM);
        if (golem >= 0 && s.ownElixir >= 2f) {
            float x = enemyX < 0.5f ? 0.60f : 0.40f;
            return play(s, golem, CardCatalog.ICE_GOLEM,
                    x, 0.73f, "2.6: Ice Golem kite.");
        }

        int skeletons = s.findCard(CardCatalog.SKELETONS);
        if (skeletons >= 0 && s.ownElixir >= 1f) {
            return play(s, skeletons, CardCatalog.SKELETONS,
                    enemyX, 0.72f, "2.6: Skeletons distraction.");
        }

        int spirit = s.findCard(CardCatalog.ICE_SPIRIT);
        if (spirit >= 0 && s.ownElixir >= 1f) {
            return play(s, spirit, CardCatalog.ICE_SPIRIT,
                    enemyX, 0.70f, "2.6: Ice Spirit reset.");
        }

        return null;
    }

    private Decision cycle(GameState s) {
        int skeletons = s.findCard(CardCatalog.SKELETONS);
        if (skeletons >= 0) {
            return play(s, skeletons, CardCatalog.SKELETONS,
                    0.50f, 0.66f, "2.6: Skeletons cycle.");
        }

        int spirit = s.findCard(CardCatalog.ICE_SPIRIT);
        if (spirit >= 0) {
            return play(s, spirit, CardCatalog.ICE_SPIRIT,
                    0.50f, 0.66f, "2.6: Ice Spirit cycle.");
        }

        return null;
    }

    private Decision play(GameState s, int index, int id,
                          float x, float y, String reason) {
        int cost = CardCatalog.cost(id);
        if (index < 0 || cost < 0 || s.ownElixir < cost) {
            return waitFor("Nedovoljno eliksira.");
        }

        return new Decision(ActionType.PLAY_CARD, index, id,
                clamp(x, 0.08f, 0.92f),
                clamp(y, 0.48f, 0.90f), reason);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private Decision waitFor(String reason) {
        return new Decision(ActionType.WAIT, -1,
                CardCatalog.UNKNOWN, 0f, 0f, reason);
    }
}
