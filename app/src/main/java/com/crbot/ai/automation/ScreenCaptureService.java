package com.crbot.ai.automation;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.Log;

import com.crbot.ai.vision.VisionEngine;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ScreenCaptureService extends Service {

    public static final String ACTION_STOP =
        "com.crbot.ai.action.STOP_VISION";

    private static final String TAG = "CrBotCapture";
    private static final String CHANNEL_ID = "crbot";
    private static final int NOTIFICATION_ID = 10;

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;

    private final VisionEngine vision = new VisionEngine();

    private HandlerThread captureThread;
    private Handler captureHandler;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicLong lastFrameTime = new AtomicLong(0);

    private static final long FRAME_INTERVAL_MS = 180;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForegroundCompat(createNotification());

        captureThread = new HandlerThread("CrBotCaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, ScreenCaptureService.class);
        stopIntent.setAction(ACTION_STOP);

        PendingIntent stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CrBot AI 3.0")
            .setContentText("CrBot sesija je aktivna")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "STOP ALL",
                stopPendingIntent
            )
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            BotRuntime.stopAll(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent == null || projection != null) {
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("resultCode", 0);

        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(
                "projectionData",
                Intent.class
            );
        } else {
            data = intent.getParcelableExtra("projectionData");
        }

        if (data == null) {
            Log.e(TAG, "Projection data nedostaje.");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager manager =
                (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);

            projection = manager.getMediaProjection(resultCode, data);

            if (projection == null) {
                Log.e(TAG, "MediaProjection nije dostupna.");
                stopSelf();
                return START_NOT_STICKY;
            }

            projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.i(TAG, "MediaProjection zaustavljena.");
                        stopCapture();
                        stopSelf();
                    }
                },
                new Handler(Looper.getMainLooper())
            );

            int width = getResources()
                .getDisplayMetrics().widthPixels;
            int height = getResources()
                .getDisplayMetrics().heightPixels;
            int density = getResources()
                .getDisplayMetrics().densityDpi;

            reader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                3
            );

            reader.setOnImageAvailableListener(
                this::processLatestImage,
                captureHandler
            );

            display = projection.createVirtualDisplay(
                "CrBotVision",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(),
                null,
                captureHandler
            );

            running = true;
            VisionStatus.onCaptureStarted();
            Log.i(TAG, "Screen capture pokrenut.");

        } catch (Exception e) {
            Log.e(TAG, "Pokretanje capture sustava nije uspjelo.", e);
            VisionStatus.onCaptureError(e);
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void processLatestImage(ImageReader source) {
        Image image = null;
        Bitmap padded = null;
        Bitmap frame = null;

        try {
            image = source.acquireLatestImage();

            if (image == null) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            long previous = lastFrameTime.get();

            if (now - previous < FRAME_INTERVAL_MS) {
                return;
            }

            if (!processing.compareAndSet(false, true)) {
                return;
            }

            lastFrameTime.set(now);

            Image.Plane plane = image.getPlanes()[0];

            int width = image.getWidth();
            int height = image.getHeight();

            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            padded = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            );

            padded.copyPixelsFromBuffer(plane.getBuffer());

            frame = Bitmap.createBitmap(
                padded, 0, 0, width, height
            );

            padded.recycle();
            padded = null;

            image.close();
            image = null;

            final Bitmap capturedFrame = frame;
            frame = null;

            vision.analyzeAsync(capturedFrame, state -> {
                Log.d(
                    TAG,
                    "Frame obrađen | elixir=" + state.ownElixir
                    + " | enemyMotion=" + state.enemyMotion
                    + " | ownMotion=" + state.ownMotion
                    + " | reliable=" + state.visionReliable
                );

                VisionStatus.onFrame(state);
                processing.set(false);
            });

        } catch (Exception e) {
            Log.e(TAG, "Greška kod obrade framea.", e);
            VisionStatus.onCaptureError(e);
            processing.set(false);

        } finally {
            if (image != null) {
                image.close();
            }

            if (padded != null && !padded.isRecycled()) {
                padded.recycle();
            }

            if (frame != null && !frame.isRecycled()) {
                frame.recycle();
            }
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "CrBot Vision",
            NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager =
            getSystemService(NotificationManager.class);

        manager.createNotificationChannel(channel);
    }

    private void stopCapture() {
        running = false;
        VisionStatus.onCaptureStopped();

        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }

        if (display != null) {
            display.release();
            display = null;
        }

        if (projection != null) {
            MediaProjection oldProjection = projection;
            projection = null;
            oldProjection.stop();
        }

        processing.set(false);
    }

    @Override
    public void onDestroy() {
        BotRuntime.deactivate();
        stopCapture();

        vision.close();

        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }

        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
