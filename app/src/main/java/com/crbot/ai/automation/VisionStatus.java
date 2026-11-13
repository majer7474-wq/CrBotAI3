package com.crbot.ai.automation;

import com.crbot.ai.game.GameState;
import com.crbot.ai.game.ElixirTracker;
import com.crbot.ai.game.CardCatalog;
import java.util.Arrays;

public final class VisionStatus {

    private static boolean captureActive = false;
    private static boolean battleStarted = false;
    private static long framesProcessed = 0;
    private static long ocrSuccesses = 0;
    private static long ocrErrors = 0;
    private static long lastFrameTime = 0;

    private static final ElixirTracker elixirTracker = new ElixirTracker();
    private static int[] lastStableHand = null;

    private static float ownElixir = -1f;
    private static boolean elixirDetected = false;
    private static float enemyMotion = 0f;
    private static float ownMotion = 0f;
    private static boolean visionReliable = false;
    private static String lastError = "Nema zabilježene greške.";
    private static String rawElixirOcr = "Nema OCR podataka.";

    private VisionStatus() {}

    public static synchronized void onCaptureStarted() {
        captureActive = true;
        battleStarted = false;
        lastStableHand = null;
        framesProcessed = 0;
        ocrSuccesses = 0;
        ocrErrors = 0;
        lastFrameTime = 0;
        ownElixir = -1f;
        elixirDetected = false;
        enemyMotion = 0f;
        ownMotion = 0f;
        visionReliable = false;
        lastError = "Nema zabilježene greške.";
        rawElixirOcr = "Nema OCR podataka.";
    }

    public static synchronized void onCaptureStopped() {
        captureActive = false;
        battleStarted = false;
        lastStableHand = null;
        ownElixir = -1f;
        elixirDetected = false;
    }

    public static synchronized void onCaptureError(Throwable error) {
        lastError = error == null
            ? "Nepoznata greška capture sustava."
            : (error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage());
    }

    public static synchronized void onFrame(GameState state) {
        framesProcessed++;
        lastFrameTime = System.currentTimeMillis();

        if (state == null) return;

        if (state.ocrCompleted) {
            ocrSuccesses++;
        } else {
            ocrErrors++;
            if (state.ocrErrorMessage != null &&
                !state.ocrErrorMessage.isEmpty()) {
                lastError = state.ocrErrorMessage;
            }
        }

        rawElixirOcr = state.rawElixirOcr == null
            ? "" : state.rawElixirOcr;

        // Automatsko prepoznavanje početka bitke.
        if (!battleStarted &&
            state.ocrElixirValue >= 0 &&
            state.ocrElixirValue <= 10) {
            elixirTracker.reset((float) state.ocrElixirValue);
            if (state.handDetected && state.hasUsableHand()) {
                lastStableHand = state.hand.clone();
            } else {
                lastStableHand = null;
            }
            battleStarted = true;
            ownElixir = elixirTracker.getEstimate();
            elixirDetected = true;
        }

        if (battleStarted) {
            if (state.handDetected && state.hasUsableHand()) {
                if (lastStableHand == null) {
                    lastStableHand = state.hand.clone();
                } else if (!Arrays.equals(lastStableHand, state.hand)) {
                    int removedCard = CardCatalog.UNKNOWN;
                    int removedCount = 0;
                    int addedCount = 0;

                    for (int oldId : lastStableHand) {
                        boolean found = false;
                        for (int newId : state.hand) {
                            if (oldId == newId) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            removedCard = oldId;
                            removedCount++;
                        }
                    }

                    for (int newId : state.hand) {
                        boolean found = false;
                        for (int oldId : lastStableHand) {
                            if (newId == oldId) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) addedCount++;
                    }

                    if (removedCount == 1 && addedCount == 1) {
                        int cost = CardCatalog.cost(removedCard);
                        if (cost >= 0) elixirTracker.onCardPlayed(cost);
                    }

                    lastStableHand = state.hand.clone();
                }
            }

            ownElixir = elixirTracker.getEstimate();
            elixirDetected = true;
            state.ownElixir = ownElixir;
            state.elixirDetected = true;
            state.visionReliable = state.handDetected && state.hasUsableHand();
        } else {
            ownElixir = -1f;
            elixirDetected = false;
            state.ownElixir = -1f;
            state.elixirDetected = false;
            state.visionReliable = false;
        }

        enemyMotion = state.enemyMotion;
        ownMotion = state.ownMotion;
        visionReliable = state.visionReliable;
    }

    public static synchronized void onCardPlayed(int cardId) {
        if (!battleStarted) return;

        int cost = CardCatalog.cost(cardId);
        if (cost < 0) return;

        elixirTracker.onCardPlayed(cost);
        ownElixir = elixirTracker.getEstimate();
        elixirDetected = true;
    }

    public static synchronized String getReport() {
        StringBuilder out = new StringBuilder();

        out.append("=== VISION STATUS ===\n");
        out.append("Screen capture: ")
            .append(captureActive ? "AKTIVAN" : "NEAKTIVAN")
            .append("\n");

        out.append("CrBot sesija: ")
            .append(BotRuntime.isActive() ? "AKTIVNA" : "ISKLJUCENA")
            .append("\n");

        out.append("Autoplay: ")
            .append(BotRuntime.isAutomationEnabled()
                ? "UKLJUCEN" : "ISKLJUCEN")
            .append("\n\n");

        out.append("Obradeni frameovi: ")
            .append(framesProcessed).append("\n");

        out.append("OCR uspjesi: ")
            .append(ocrSuccesses).append("\n");

        out.append("OCR greske: ")
            .append(ocrErrors).append("\n\n");

        out.append("Elixir procjena: ");

        if (battleStarted) {
            ownElixir = elixirTracker.getEstimate();
            out.append(String.format(java.util.Locale.US, "%.1f/10", ownElixir));
            out.append("\\nBrzina regeneracije: ")
                .append(elixirTracker.getPhaseLabel());
            long elapsed = elixirTracker.getElapsedSeconds();
            out.append("\\nVrijeme od starta: ")
                .append(String.format(java.util.Locale.US, "%02d:%02d",
                    elapsed / 60, elapsed % 60));
            out.append("\\nPotrošnja karata: prati se promjenom ruke");
        } else {
            out.append("čeka automatsko prepoznavanje karata i eliksira");
        }

        out.append("\\nSirovi OCR eliksira: ")
            .append(rawElixirOcr).append("\n\n");

        out.append("Kretanje protivnika: ")
            .append(Math.round(enemyMotion * 100))
            .append("%\n");

        out.append("Kretanje nase strane: ")
            .append(Math.round(ownMotion * 100))
            .append("%\n\n");

        out.append("Pouzdanost arene: ")
            .append(visionReliable ? "POTVRDENA" : "NIJE POTVRDENA")
            .append("\n");

        out.append("Automatsko prepoznavanje bitke: aktivno\n");
        out.append("Automatsko igranje: kontrolirano\n");

        if (lastFrameTime > 0) {
            long age = Math.max(0,
                (System.currentTimeMillis() - lastFrameTime) / 1000);
            out.append("Zadnji frame prije: ")
                .append(age).append(" s\n");
        } else {
            out.append("Zadnji frame: nema podataka\n");
        }

        out.append("\nZadnja greska: ").append(lastError);
        return out.toString();
    }
}
