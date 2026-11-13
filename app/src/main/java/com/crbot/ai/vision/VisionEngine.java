package com.crbot.ai.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;

import com.crbot.ai.game.GameState;
import com.crbot.ai.game.CardCatalog;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.regex.Pattern;
import java.util.Arrays;

public class VisionEngine {

    public interface Callback {
        void onResult(GameState state);
    }

    private final TextRecognizer recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    private final CardTemplateRecognizer cardRecognizer;
    private int[] pendingHand = {
        CardCatalog.UNKNOWN, CardCatalog.UNKNOWN,
        CardCatalog.UNKNOWN, CardCatalog.UNKNOWN
    };
    private int pendingHandCount = 0;

    private byte[] previousPixels;
    private volatile GameState latestState = new GameState();

    private final long[] previousCards = new long[4];

    // Stabilizacija OCR rezultata.
    private int pendingElixir = -1;
    private int pendingCount = 0;
    private int acceptedElixir = -1;
    private long acceptedElixirTime = 0;

    private static final int OCR_SCALE = 3;
    private static final int REQUIRED_STABLE_READS = 2;

    private static final Pattern ELIXIR_PATTERN =
        Pattern.compile("^(10|[0-9])$");

    public VisionEngine(Context context) {
        cardRecognizer = new CardTemplateRecognizer(context.getAssets());
    }

    public GameState getLatestState() {
        return latestState;
    }

    public void analyzeAsync(Bitmap frame, Callback callback) {
        if (frame == null || frame.isRecycled()) {
            return;
        }

        GameState state = new GameState();
        Bitmap crop = null;
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
                    "Neispravno OCR podrucje eliksira"
                );
            }

            crop = Bitmap.createBitmap(
                frame, left, top, right - left, bottom - top
            );

            ocrFrame = Bitmap.createScaledBitmap(
                crop,
                crop.getWidth() * OCR_SCALE,
                crop.getHeight() * OCR_SCALE,
                true
            );

            if (crop != ocrFrame && !crop.isRecycled()) {
                crop.recycle();
            }
            crop = null;

            InputImage image = InputImage.fromBitmap(ocrFrame, 0);
            final Bitmap finalOcrFrame = ocrFrame;
            final int ocrWidth = ocrFrame.getWidth();
            final int ocrHeight = ocrFrame.getHeight();

            recognizer.process(image)
                .addOnSuccessListener(result -> {
                    try {
                        state.ocrCompleted = true;
                        state.ocrErrorMessage = "";

                        readText(
                            result,
                            state,
                            ocrWidth,
                            ocrHeight
                        );

                        state.lastUpdate = System.currentTimeMillis();
                        latestState = state;

                        if (callback != null) {
                            callback.onResult(state);
                        }
                    } catch (Exception e) {
                        Log.e("CrBotVision", "OCR result error", e);
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
                ? "Greska obrade slike"
                : e.getMessage();

            state.visionReliable = false;
            state.lastUpdate = System.currentTimeMillis();
            latestState = state;

            if (callback != null) {
                callback.onResult(state);
            }

            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
            if (ocrFrame != null && !ocrFrame.isRecycled()) {
                ocrFrame.recycle();
            }
            if (!frame.isRecycled()) {
                frame.recycle();
            }
        }
    }

    private void readText(
        Text result,
        GameState state,
        int imageWidth,
        int imageHeight
    ) {
        StringBuilder debug = new StringBuilder();
        int bestCandidate = -1;
        double bestScore = Double.MAX_VALUE;

        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {

                    String value = element.getText().trim();
                    Rect box = element.getBoundingBox();

                    if (debug.length() < 700) {
                        debug.append(value);

                        if (box != null) {
                            debug.append("@[")
                                .append(box.left).append(",")
                                .append(box.top).append(",")
                                .append(box.right).append(",")
                                .append(box.bottom).append("]");
                        }

                        debug.append(" ");
                    }

                    // Prihvacamo samo element koji je tocno broj.
                    if (!ELIXIR_PATTERN.matcher(value).matches()) {
                        continue;
                    }

                    if (box == null || imageWidth <= 0 || imageHeight <= 0) {
                        continue;
                    }

                    float centerX =
                        ((box.left + box.right) / 2f) / imageWidth;

                    float centerY =
                        ((box.top + box.bottom) / 2f) / imageHeight;

                    float boxHeight =
                        (box.bottom - box.top) / (float) imageHeight;

                    // Odbaci elemente izvan ocekivanog podrucja.
                    if (centerX < 0.03f || centerX > 0.97f ||
                        centerY < 0.08f || centerY > 0.98f ||
                        boxHeight < 0.025f || boxHeight > 0.95f) {
                        continue;
                    }

                    int number = Integer.parseInt(value);

                    // Prednost daje broju u donjem lijevom dijelu izreza.
                    double score =
                        Math.abs(centerX - 0.55f) +
                        Math.abs(centerY - 0.72f) * 1.2;

                    if (score < bestScore) {
                        bestScore = score;
                        bestCandidate = number;
                    }
                }
            }
        }

        state.rawElixirOcr = debug.toString();

        if (bestCandidate >= 0) {
            if (bestCandidate == pendingElixir) {
                pendingCount++;
            } else {
                pendingElixir = bestCandidate;
                pendingCount = 1;
            }

            if (pendingCount >= REQUIRED_STABLE_READS) {
                acceptedElixir = bestCandidate;
                acceptedElixirTime = System.currentTimeMillis();
            }
        } else {
            pendingElixir = -1;
            pendingCount = 0;
        }

        state.ocrElixirValue = acceptedElixir >= 0 &&
            System.currentTimeMillis() - acceptedElixirTime <= 2500
                ? acceptedElixir : -1;

        // OCR brojevi nisu trajni izvor eliksira.
        // ElixirTracker u VisionStatus vodi procjenu.
        state.ownElixir = -1;
        state.elixirDetected = false;
        state.visionReliable = false;

        Log.d(
            "CrBotVision",
            "OCR raw=" + state.rawElixirOcr +
            " candidate=" + bestCandidate +
            " stable=" + pendingCount +
            " accepted=" + acceptedElixir
        );
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
        int[] detected = new int[4];

        for (int card = 0; card < 4; card++) {
            float left = VisionConfig.HAND_CARD_LEFT
                + card * VisionConfig.HAND_CARD_STEP;
            float right = left + VisionConfig.HAND_CARD_WIDTH;

            int x1 = Math.max(0, (int) (width * left));
            int x2 = Math.min(width, (int) (width * right));
            int y1 = Math.max(0, (int) (height * VisionConfig.HAND_TOP));
            int y2 = Math.min(height,
                (int) (height * VisionConfig.HAND_BOTTOM));

            if (x2 <= x1 || y2 <= y1) {
                detected[card] = CardCatalog.UNKNOWN;
                continue;
            }

            Rect bounds = new Rect(x1, y1, x2, y2);
            CardTemplateRecognizer.Result result =
                cardRecognizer.recognize(frame, bounds);

            detected[card] = result.cardId;

            long hash = 0;
            int bit = 0;

            for (int gy = 0; gy < 4; gy++) {
                for (int gx = 0; gx < 4; gx++) {
                    int x = x1 + (x2 - x1) * gx / 4;
                    int y = y1 + (y2 - y1) * gy / 4;

                    int color = frame.getPixel(x, y);
                    int gray = (
                        Color.red(color) +
                        Color.green(color) +
                        Color.blue(color)
                    ) / 3;

                    hash ^= ((long) gray << (bit % 56));
                    bit++;
                }
            }

            state.cardFingerprints[card] = hash;
            previousCards[card] = hash;
        }

        if (Arrays.equals(detected, pendingHand)) {
            pendingHandCount++;
        } else {
            pendingHand = detected.clone();
            pendingHandCount = 1;
        }

        boolean allKnown = true;
        for (int id : pendingHand) {
            if (!CardCatalog.isKnown(id)) {
                allKnown = false;
                break;
            }
        }

        state.handDetected = pendingHandCount >= 2 && allKnown;

        if (state.handDetected) {
            state.hand = pendingHand.clone();
        } else {
            Arrays.fill(state.hand, CardCatalog.UNKNOWN);
        }
    }


    public void close() {
        recognizer.close();
    }
}
