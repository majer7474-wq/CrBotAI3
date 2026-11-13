package com.crbot.ai.automation;

import com.crbot.ai.game.GameState;

public final class VisionStatus {

    private static boolean captureActive = false;
    private static long framesProcessed = 0;
    private static long ocrSuccesses = 0;
    private static long ocrErrors = 0;
    private static long lastFrameTime = 0;

    private static float ownElixir = -1f;
    private static boolean elixirDetected = false;
    private static float enemyMotion = 0f;
    private static float ownMotion = 0f;
    private static boolean visionReliable = false;
    private static String lastError = "Nema zabilježene greške.";

    private VisionStatus() {}

    public static synchronized void onCaptureStarted() {
        captureActive = true;
        framesProcessed = 0;
        ocrSuccesses = 0;
        ocrErrors = 0;
        lastFrameTime = 0;
        ownElixir = -1;
        elixirDetected = false;
        enemyMotion = 0f;
        ownMotion = 0f;
        visionReliable = false;
        lastError = "Nema zabilježene greške.";
        rawElixirOcr = "Nema OCR podataka.";
    }

    public static synchronized void onCaptureStopped() {
        captureActive = false;
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

        ownElixir = state.ownElixir;
        rawElixirOcr = state.rawElixirOcr == null ? "" : state.rawElixirOcr;
        elixirDetected = state.elixirDetected;
        enemyMotion = state.enemyMotion;
        ownMotion = state.ownMotion;
        visionReliable = state.visionReliable;
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

        out.append("Elixir OCR procjena: ");

        if (elixirDetected && ownElixir >= 0) {
            out.append(ownElixir).append("/10");
        } else {
            out.append("nije ocitan");
        }

        out.append("\n");

        out.append("Kretanje protivnika: ")
            .append(Math.round(enemyMotion * 100))
            .append("%\n");

        out.append("Kretanje nase strane: ")
            .append(Math.round(ownMotion * 100))
            .append("%\n\n");

        out.append("Pouzdanost arene: ")
            .append(visionReliable ? "POTVRDENA" : "NIJE POTVRDENA")
            .append("\n");

        out.append("Prepoznavanje karata: NIJE IMPLEMENTIRANO\n");
        out.append("Automatsko igranje: NIJE IMPLEMENTIRANO\n");

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
