package com.crbot.ai.vision;

public final class VisionConfig {
    private VisionConfig() {}

    public static final float ARENA_LEFT = 0.04f;
    public static final float ARENA_RIGHT = 0.96f;
    public static final float ARENA_TOP = 0.08f;
    public static final float ARENA_BOTTOM = 0.78f;

    public static final float ENEMY_DIVIDER = 0.43f;
    public static final float OWN_DIVIDER = 0.57f;

    public static final float HAND_TOP = 0.79f;
    public static final float HAND_BOTTOM = 0.99f;

    public static final float ELIXIR_LEFT = 0.00f;
    public static final float ELIXIR_RIGHT = 0.24f;
    public static final float ELIXIR_TOP = 0.82f;
    public static final float ELIXIR_BOTTOM = 0.99f;

    public static final int SAMPLE_WIDTH = 64;
    public static final int SAMPLE_HEIGHT = 96;
    public static final int MOTION_THRESHOLD = 24;
}
