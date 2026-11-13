package com.crbot.ai.vision;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;

import com.crbot.ai.game.CardCatalog;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CardTemplateRecognizer {

    public static final class Result {
        public final int cardId;
        public final float confidence;
        public final float margin;

        Result(int id, float confidence, float margin) {
            this.cardId = id;
            this.confidence = confidence;
            this.margin = margin;
        }
    }

    private static final int W = 48;
    private static final int H = 64;
    private static final int GRID = 4;
    private static final int H_BINS = 8;
    private static final int S_BINS = 2;
    private static final int V_BINS = 2;
    private static final int FEATURES =
        GRID * GRID * H_BINS * S_BINS * V_BINS;

    private final Map<Integer, float[]> templates =
        new LinkedHashMap<>();

    public CardTemplateRecognizer(AssetManager assets) {
        load(assets, CardCatalog.HOG_RIDER, "hog_rider.png");
        load(assets, CardCatalog.MUSKETEER, "musketeer.png");
        load(assets, CardCatalog.ICE_GOLEM, "ice_golem.png");
        load(assets, CardCatalog.ICE_SPIRIT, "ice_spirit.png");
        load(assets, CardCatalog.SKELETONS, "skeletons.png");
        load(assets, CardCatalog.CANNON, "cannon.png");
        load(assets, CardCatalog.FIREBALL, "fireball.png");
        load(assets, CardCatalog.THE_LOG, "the_log.png");
    }

    private void load(AssetManager assets, int id, String file) {
        try (InputStream input = assets.open("cards/" + file)) {
            Bitmap source = BitmapFactory.decodeStream(input);
            if (source == null) return;

            Bitmap scaled = Bitmap.createScaledBitmap(
                source, W, H, true
            );

            templates.put(id, describe(scaled));

            if (scaled != source) scaled.recycle();
            source.recycle();

        } catch (Exception ignored) {
        }
    }

    public boolean isReady() {
        return templates.size() == 8;
    }

    public Result recognize(Bitmap frame, Rect bounds) {
        if (frame == null || frame.isRecycled() ||
            bounds == null || templates.size() != 8) {
            return new Result(CardCatalog.UNKNOWN, 0f, 0f);
        }

        Rect safe = new Rect(
            Math.max(0, bounds.left),
            Math.max(0, bounds.top),
            Math.min(frame.getWidth(), bounds.right),
            Math.min(frame.getHeight(), bounds.bottom)
        );

        if (safe.width() < 12 || safe.height() < 12) {
            return new Result(CardCatalog.UNKNOWN, 0f, 0f);
        }

        // Makni rubove karte i uzmi središnji dio ilustracije.
        int insetX = Math.round(safe.width() * 0.10f);
        int insetY = Math.round(safe.height() * 0.08f);

        safe.inset(insetX, insetY);

        if (safe.width() < 8 || safe.height() < 8) {
            return new Result(CardCatalog.UNKNOWN, 0f, 0f);
        }

        Bitmap crop = Bitmap.createBitmap(
            frame,
            safe.left,
            safe.top,
            safe.width(),
            safe.height()
        );

        Bitmap scaled = Bitmap.createScaledBitmap(
            crop, W, H, true
        );

        float[] feature = describe(scaled);

        if (scaled != crop) scaled.recycle();
        crop.recycle();

        int bestId = CardCatalog.UNKNOWN;
        float best = Float.MAX_VALUE;
        float second = Float.MAX_VALUE;

        for (Map.Entry<Integer, float[]> entry : templates.entrySet()) {
            float distance = distance(feature, entry.getValue());

            if (distance < best) {
                second = best;
                best = distance;
                bestId = entry.getKey();
            } else if (distance < second) {
                second = distance;
            }
        }

        float confidence = 1f / (1f + best * 5f);
        float margin = second == Float.MAX_VALUE
            ? 0f
            : Math.max(0f, second - best);

        // Konzervativno: ne pogađaj kartu ako rezultat nije jasan.
        if (confidence < 0.28f || margin < 0.012f) {
            bestId = CardCatalog.UNKNOWN;
        }

        return new Result(bestId, confidence, margin);
    }

    private float[] describe(Bitmap bitmap) {
        float[] result = new float[FEATURES];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int color = bitmap.getPixel(x, y);

                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);

                int hb = Math.min(
                    H_BINS - 1,
                    (int) (hsv[0] / 360f * H_BINS)
                );
                int sb = hsv[1] >= 0.5f ? 1 : 0;
                int vb = hsv[2] >= 0.5f ? 1 : 0;

                int gx = Math.min(GRID - 1, x * GRID / W);
                int gy = Math.min(GRID - 1, y * GRID / H);

                int index =
                    (((gy * GRID + gx) * H_BINS + hb)
                    * S_BINS + sb) * V_BINS + vb;

                result[index]++;
            }
        }

        int pixelsPerCell = (W / GRID) * (H / GRID);

        for (int i = 0; i < result.length; i++) {
            result[i] /= pixelsPerCell;
        }

        return result;
    }

    private float distance(float[] a, float[] b) {
        float total = 0f;

        for (int i = 0; i < a.length; i++) {
            float sum = a[i] + b[i];

            if (sum > 0f) {
                float difference = a[i] - b[i];
                total += difference * difference / sum;
            }
        }

        return total / a.length;
    }
}
