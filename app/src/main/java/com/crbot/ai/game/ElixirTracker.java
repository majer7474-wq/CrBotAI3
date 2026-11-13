package com.crbot.ai.game;

public class ElixirTracker {

    // Standardna bitka: double nakon 2:00, triple nakon 2:30.
    private static final long DOUBLE_ELIXIR_AT_NS =
        120_000_000_000L;
    private static final long TRIPLE_ELIXIR_AT_NS =
        150_000_000_000L;

    private static final float BASE_SECONDS_PER_ELIXIR = 2.8f;

    private float estimated = 5.0f;
    private long battleStartNanos = 0L;
    private long lastUpdateNanos = System.nanoTime();
    private boolean battleStarted = false;

    public synchronized void startBattle() {
        reset(5.0f);
    }

    public synchronized float update(float elapsedSeconds) {
        updateToNow();
        return estimated;
    }

    public synchronized void onCardPlayed(int cost) {
        if (!battleStarted || cost < 0 || cost > 10) return;

        updateToNow();
        estimated = Math.max(0f, estimated - cost);
    }

    public synchronized float getEstimate() {
        updateToNow();
        return estimated;
    }

    public synchronized void reset(float value) {
        estimated = Math.max(0f, Math.min(10f, value));
        battleStartNanos = System.nanoTime();
        lastUpdateNanos = battleStartNanos;
        battleStarted = true;
    }

    public synchronized long getElapsedSeconds() {
        if (!battleStarted) return 0L;

        return Math.max(0L,
            (System.nanoTime() - battleStartNanos)
                / 1_000_000_000L);
    }

    public synchronized int getMultiplier() {
        if (!battleStarted) return 1;

        long elapsed = System.nanoTime() - battleStartNanos;

        if (elapsed >= TRIPLE_ELIXIR_AT_NS) return 3;
        if (elapsed >= DOUBLE_ELIXIR_AT_NS) return 2;

        return 1;
    }

    public synchronized String getPhaseLabel() {
        return "x" + getMultiplier();
    }

    private void updateToNow() {
        if (!battleStarted) return;

        long now = System.nanoTime();
        if (now <= lastUpdateNanos) return;

        long cursor = lastUpdateNanos;

        while (cursor < now && estimated < 10f) {
            long elapsed = cursor - battleStartNanos;

            int multiplier;
            long nextBoundary;

            if (elapsed < DOUBLE_ELIXIR_AT_NS) {
                multiplier = 1;
                nextBoundary =
                    battleStartNanos + DOUBLE_ELIXIR_AT_NS;
            } else if (elapsed < TRIPLE_ELIXIR_AT_NS) {
                multiplier = 2;
                nextBoundary =
                    battleStartNanos + TRIPLE_ELIXIR_AT_NS;
            } else {
                multiplier = 3;
                nextBoundary = Long.MAX_VALUE;
            }

            long segmentEnd = Math.min(now, nextBoundary);
            if (segmentEnd <= cursor) segmentEnd = now;

            float seconds =
                (segmentEnd - cursor) / 1_000_000_000f;

            estimated = Math.min(
                10f,
                estimated + seconds * multiplier
                    / BASE_SECONDS_PER_ELIXIR
            );

            cursor = segmentEnd;
        }

        lastUpdateNanos = now;
    }
}
