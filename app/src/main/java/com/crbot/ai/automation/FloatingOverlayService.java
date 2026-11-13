package com.crbot.ai.automation;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.crbot.ai.game.CardCatalog;

public class FloatingOverlayService extends Service {
    private WindowManager wm;
    private View bubble;
    private View panel;
    private TextView report;
    private final Handler handler = new Handler();
    private int startX, startY;
    private float touchX, touchY;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (report != null) {
                String text = VisionStatus.getReport();
                if (!text.contentEquals(report.getText())) {
                    report.setText(text);
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
        handler.post(ticker);
    }

    private WindowManager.LayoutParams params(int width, int height) {
        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(100);
        return lp;
    }

    private void showBubble() {
        if (bubble != null) return;

        TextView b = new TextView(this);
        b.setText("CB");
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.rgb(25, 95, 180));
        b.setPadding(18, 14, 18, 14);
        bubble = b;

        b.setOnClickListener(v -> {
            if (panel == null) showPanel();
            else hidePanel();
        });

        b.setOnTouchListener((v, e) -> {
            WindowManager.LayoutParams lp =
                (WindowManager.LayoutParams) bubble.getLayoutParams();

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                startX = lp.x;
                startY = lp.y;
                touchX = e.getRawX();
                touchY = e.getRawY();
                return false;
            }

            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                lp.x = startX + (int)(e.getRawX() - touchX);
                lp.y = startY + (int)(e.getRawY() - touchY);
                wm.updateViewLayout(bubble, lp);
                return true;
            }
            return false;
        });

        wm.addView(bubble, params(dp(52), dp(52)));
    }

    private void showPanel() {
        if (panel != null) return;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackgroundColor(Color.rgb(28, 32, 40));

        TextView title = new TextView(this);
        title.setText("CRBOT AI 3.0");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        box.addView(title);

        report = new TextView(this);
        report.setTextColor(Color.WHITE);
        report.setTextSize(13);
        report.setPadding(dp(6), dp(8), dp(6), dp(8));
        report.setText(VisionStatus.getReport());

        ScrollView reportScroll = new ScrollView(this);
        reportScroll.setFillViewport(false);
        reportScroll.addView(report);

        box.addView(reportScroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)
        ));

        TextView cardTitle = new TextView(this);
        cardTitle.setText("RUČNO ODUZMI ELIXIR - nakon odigrane karte:");
        cardTitle.setTextColor(Color.WHITE);
        cardTitle.setTextSize(12);
        box.addView(cardTitle);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row1);

        addCardButton(row1, "Hog 4", CardCatalog.HOG_RIDER);
        addCardButton(row1, "Musk 4", CardCatalog.MUSKETEER);
        addCardButton(row1, "Golem 2", CardCatalog.ICE_GOLEM);
        addCardButton(row1, "Spirit 1", CardCatalog.ICE_SPIRIT);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row2);

        addCardButton(row2, "Skel 1", CardCatalog.SKELETONS);
        addCardButton(row2, "Cannon 3", CardCatalog.CANNON);
        addCardButton(row2, "Fireball 4", CardCatalog.FIREBALL);
        addCardButton(row2, "Log 2", CardCatalog.THE_LOG);

        Button start = new Button(this);
        start.setText("1. START BOT");
        start.setOnClickListener(v -> {
            if (!ScreenCaptureService.isRunning()) {
                report.setText("Prvo pokreni START VISION u aplikaciji.");
                return;
            }
            BotRuntime.enableAutomation();
            report.setText(VisionStatus.getReport());
        });
        box.addView(start);

        Button stop = new Button(this);
        stop.setText("2. STOP BOT");
        stop.setOnClickListener(v -> {
            BotRuntime.stopAll(this);
            report.setText("BOT ZAUSTAVLJEN");
        });
        box.addView(stop);

        Button minimize = new Button(this);
        minimize.setText("3. MINIMIZIRAJ");
        minimize.setOnClickListener(v -> hidePanel());
        box.addView(minimize);

        Button remove = new Button(this);
        remove.setText("4. UKLONI PANEL");
        remove.setOnClickListener(v -> stopSelf());
        box.addView(remove);

        Button exit = new Button(this);
        exit.setText("5. IZLAZ SVE - STOP BOT");
        exit.setOnClickListener(v -> {
            BotRuntime.stopAll(this);
            stopSelf();
        });
        box.addView(exit);

        panel = box;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        WindowManager.LayoutParams lp = params(
            screenWidth - dp(24), WindowManager.LayoutParams.WRAP_CONTENT
        );
        lp.x = dp(12);
        lp.y = dp(24);
        wm.addView(panel, lp);
    }

    private void addCardButton(
        LinearLayout row,
        String label,
        int cardId
    ) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setOnClickListener(v -> {
            VisionStatus.onCardPlayed(cardId);
            if (report != null) {
                report.setText(VisionStatus.getReport());
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        );
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        row.addView(button, lp);
    }

    private void hidePanel() {
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {}
            panel = null;
            report = null;
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        hidePanel();

        if (bubble != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {}
            bubble = null;
        }

        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
