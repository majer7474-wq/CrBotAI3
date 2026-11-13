package com.crbot.ai.game;

public class GameState {

    public float ownElixir = -1f;
    public float enemyElixir = -1f;

    // Card IDs in the four visible hand positions.
    public int[] hand = {
        CardCatalog.UNKNOWN,
        CardCatalog.UNKNOWN,
        CardCatalog.UNKNOWN,
        CardCatalog.UNKNOWN
    };

    public long[] cardFingerprints = new long[4];

    public int ownTowerHealth = -1;
    public int enemyTowerHealth = -1;

    public boolean enemyThreatDetected = false;
    public boolean visionReliable = false;
    public boolean elixirDetected = false;
    public boolean ocrCompleted = false;
    public String ocrErrorMessage = "";
    public boolean handDetected = false;

    public boolean enemyTroopDetected = false;
    public boolean enemyTroopTargetsAir = false;
    public boolean enemyTroopOnOurSide = false;

    // Normalized screen coordinates, 0.0 to 1.0.
    public float enemyTroopX = 0.5f;
    public float enemyTroopY = 0.5f;

    public float enemyMotion = 0f;
    public float ownMotion = 0f;

    public long lastUpdate = System.currentTimeMillis();

    public boolean hasUsableHand() {
        if (hand == null || hand.length != 4) return false;

        for (int id : hand) {
            if (!CardCatalog.isKnown(id)) return false;
        }

        return true;
    }

    public int findCard(int cardId) {
        if (hand == null) return -1;

        for (int i = 0; i < hand.length; i++) {
            if (hand[i] == cardId) return i;
        }

        return -1;
    }

    public boolean isFresh() {
        long age = System.currentTimeMillis() - lastUpdate;
        return age >= 0 && age <= 1200;
    }
}
