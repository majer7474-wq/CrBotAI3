package com.crbot.ai.game;

public final class CardCatalog {

    private CardCatalog() {}

    public static final int UNKNOWN = -1;

    public static final int HOG_RIDER = 1;
    public static final int MUSKETEER = 2;
    public static final int ICE_GOLEM = 3;
    public static final int SKELETONS = 4;
    public static final int ICE_SPIRIT = 5;
    public static final int CANNON = 6;
    public static final int FIREBALL = 7;
    public static final int THE_LOG = 8;

    public static int cost(int id) {
        switch (id) {
            case HOG_RIDER: return 4;
            case MUSKETEER: return 4;
            case ICE_GOLEM: return 2;
            case SKELETONS: return 1;
            case ICE_SPIRIT: return 1;
            case CANNON: return 3;
            case FIREBALL: return 4;
            case THE_LOG: return 2;
            default: return -1;
        }
    }

    public static String name(int id) {
        switch (id) {
            case HOG_RIDER: return "Hog Rider";
            case MUSKETEER: return "Musketeer";
            case ICE_GOLEM: return "Ice Golem";
            case SKELETONS: return "Skeletons";
            case ICE_SPIRIT: return "Ice Spirit";
            case CANNON: return "Cannon";
            case FIREBALL: return "Fireball";
            case THE_LOG: return "The Log";
            default: return "Unknown";
        }
    }

    public static boolean isKnown(int id) {
        return cost(id) >= 0;
    }
}
