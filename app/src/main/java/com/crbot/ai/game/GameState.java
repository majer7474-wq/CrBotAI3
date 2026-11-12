package com.crbot.ai.game;

public class GameState {
    public float ownElixir = 0;
    public float enemyElixir = -1;

    public int[] hand = {-1, -1, -1, -1};
    public long[] cardFingerprints = new long[4];

    public int ownTowerHealth = -1;
    public int enemyTowerHealth = -1;

    public boolean enemyThreatDetected = false;
    public boolean visionReliable = false;
    public boolean elixirDetected = false;
    public boolean handDetected = false;

    public float enemyMotion = 0;
    public float ownMotion = 0;

    public long lastUpdate = System.currentTimeMillis();
}
