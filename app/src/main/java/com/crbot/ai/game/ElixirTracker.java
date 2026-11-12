package com.crbot.ai.game;

public class ElixirTracker {

    private float estimated = 5.0f;
    private long lastUpdate = System.currentTimeMillis();

    public synchronized float update(float elapsedSeconds) {
        estimated = Math.min(10f, estimated + elapsedSeconds / 2.8f);
        return estimated;
    }

    public synchronized void onCardPlayed(int cost) {
        estimated = Math.max(0f, estimated - cost);
        lastUpdate = System.currentTimeMillis();
    }

    public synchronized float getEstimate() {
        long now = System.currentTimeMillis();
        float elapsed = (now - lastUpdate) / 1000f;
        update(elapsed);
        lastUpdate = now;
        return estimated;
    }

    public synchronized void reset(float value) {
        estimated = Math.max(0f, Math.min(10f, value));
        lastUpdate = System.currentTimeMillis();
    }
}
