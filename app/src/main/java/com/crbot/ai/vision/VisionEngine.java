package com.crbot.ai.vision;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;

import com.crbot.ai.game.GameState;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VisionEngine {

    public interface Callback {
        void onResult(GameState state);
    }

    private final TextRecognizer recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private byte[] previousPixels;
    private volatile GameState latestState = new GameState();

    private long[] previousCards = new long[4];

    public GameState getLatestState() {
        return latestState;
    }

    public void analyzeAsync(Bitmap frame, Callback callback) {
        if (frame == null || frame.isRecycled()) {
            return;
        }

        GameState state = new GameState();
        Bitmap ocrFrame = null;

        try {
            calculateMotion(frame, state);
            calculateCardFingerprints(frame, state);

            int width = frame.getWidth();
            int height = frame.getHeight();

            int left = Math.max(0,
                (int) (width * VisionConfig.ELIXIR_LEFT));
            int top = Math.max(0,
                (int) (height * VisionConfig.ELIXIR_TOP));
            int right = Math.min(width,
                (int) (width * VisionConfig.ELIXIR_RIGHT));
            int bottom = Math.min(height,
                (int) (height * VisionConfig.ELIXIR_BOTTOM));

            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException(
                    "Neispravno OCR područje eliksira"
                );
            }

            ocrFrame = Bitmap.createBitmap(
                frame, left, top, right - left, bottom - top
            );

            InputImage image = InputImage.fromBitmap(ocrFrame, 0);
            final Bitmap finalOcrFrame = ocrFrame;

            recognizer.process(image)
                .addOnSuccessListener(text -> {
                    try {
                        state.ocrCompleted = true;
                        state.ocrErrorMessage = "";
                        readText(text, state);
                        state.lastUpdate = System.currentTimeMillis();
                        latestState = state;

                        if (callback != null) {
                            callback.onResult(state);
                        }
                    } finally {
                        if (!finalOcrFrame.isRecycled()) {
                            finalOcrFrame.recycle();
                        }
                        if (!frame.isRecycled()) {
                            frame.recycle();
                        }
                    }
                })
                .addOnFailureListener(error -> {
                    try {
                        Log.e("CrBotVision", "OCR error", error);

                        state.ocrCompleted = false;
                        state.ocrErrorMessage =
                            error.getMessage() == null
                                ? "OCR obrada nije uspjela"
                                : error.getMessage();

                        state.visionReliable = false;
                        state.lastUpdate = System.currentTimeMillis();
                        latestState = state;

                        if (callback != null) {
                            callback.onResult(state);
                        }
                    } finally {
                        if (!finalOcrFrame.isRecycled()) {
                            finalOcrFrame.recycle();
                        }
                        if (!frame.isRecycled()) {
                            frame.recycle();
                        }
                    }
                });

        } catch (Exception e) {
            Log.e("CrBotVision", "Image analysis error", e);

            state.ocrCompleted = false;
            state.ocrErrorMessage = e.getMessage() == null
                ? "Greška obrade slike"
                : e.getMessage();

            state.visionReliable = false;
            state.lastUpdate = System.currentTimeMillis();
            latestState = state;

            if (callback != null) {
                callback.onResult(state);
            }

            if (ocrFrame != null && !ocrFrame.isRecycled()) {
                ocrFrame.recycle();
            }
            if (!frame.isRecycled()) {
                frame.recycle();
            }
        }
    }

    private void readText(Text result, GameState state) {
        StringBuilder allText = new StringBuilder();

        for (Text.TextBlock block : result.getTextBlocks()) {
            allText.append(block.getText()).append(" ");
        }

        String text = allText.toString();

        // OCR may see other numbers too. Only accept an
        // isolated value from 0 to 10 as a preliminary estimate.
        Pattern pattern = Pattern.compile(
            "(?<!\\d)(10|[0-9])(?!\\d)"
        );

        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));

                if (value >= 0 && value <= 10) {
                    state.ownElixir = value;
                    state.elixirDetected = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // OCR alone is not sufficient to confirm the whole arena.
        state.visionReliable = false;
    }

    private void calculateMotion(Bitmap frame, GameState state) {
        Bitmap small = Bitmap.createScaledBitmap(
            frame,
            VisionConfig.SAMPLE_WIDTH,
            VisionConfig.SAMPLE_HEIGHT,
            true
        );

        int width = small.getWidth();
        int height = small.getHeight();

        byte[] current = new byte[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = small.getPixel(x, y);

                int gray = (
                    Color.red(pixel) * 30 +
                    Color.green(pixel) * 59 +
                    Color.blue(pixel) * 11
                ) / 100;

                current[y * width + x] = (byte) gray;
            }
        }

        if (previousPixels != null &&
            previousPixels.length == current.length) {

            int enemyChanges = 0;
            int ownChanges = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;

                    int oldValue = previousPixels[index] & 255;
                    int newValue = current[index] & 255;

                    if (Math.abs(oldValue - newValue) >
                        VisionConfig.MOTION_THRESHOLD) {

                        if (y < height / 2) {
                            enemyChanges++;
                        } else {
                            ownChanges++;
                        }
                    }
                }
            }

            state.enemyMotion =
                enemyChanges / (float) (width * height / 2);

            state.ownMotion =
                ownChanges / (float) (width * height / 2);
        }

        previousPixels = current;
        small.recycle();
    }

    private void calculateCardFingerprints(
        Bitmap frame,
        GameState state
    ) {
        int width = frame.getWidth();
        int height = frame.getHeight();

        for (int card = 0; card < 4; card++) {
            float left = 0.05f + card * 0.225f;
            float right = left + 0.19f;

            int x1 = Math.max(0, (int) (width * left));
            int x2 = Math.min(width, (int) (width * right));
            int y1 = (int) (height * VisionConfig.HAND_TOP);
            int y2 = Math.min(
                height,
                (int) (height * VisionConfig.HAND_BOTTOM)
            );

            long hash = 0;
            int bit = 0;

            for (int gy = 0; gy < 4; gy++) {
                for (int gx = 0; gx < 4; gx++) {
                    int x = x1 + (x2 - x1) * gx / 4;
                    int y = y1 + (y2 - y1) * gy / 4;

                    if (x < width && y < height) {
                        int color = frame.getPixel(x, y);
                        int gray = (
                            Color.red(color) +
                            Color.green(color) +
                            Color.blue(color)
                        ) / 3;

                        hash ^= ((long) gray << (bit % 56));
                    }

                    bit++;
                }
            }

            state.cardFingerprints[card] = hash;
            previousCards[card] = hash;
        }
    }

    public void close() {
        recognizer.close();
    }
}
