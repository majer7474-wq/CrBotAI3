package com.crbot.ai;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crbot.ai.automation.ScreenCaptureService;
import com.crbot.ai.automation.BotRuntime;
import com.crbot.ai.automation.VisionStatus;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 1001;

    private MediaProjectionManager projectionManager;
    private TextView status;
    private Button startButton;
    private Button stopButton;
    private Button battleStartButton;
    private TextView visionDetails;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectionManager = (MediaProjectionManager)
            getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 40, 32, 32);

        TextView title = new TextView(this);
        title.setText("CrBot AI 3.0");
        title.setTextSize(28);
        layout.addView(title);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 24, 0, 24);
        layout.addView(status);

        Button accessibility = new Button(this);
        accessibility.setText("1. Uključi Accessibility");
        accessibility.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        layout.addView(accessibility);

        startButton = new Button(this);
        startButton.setText("START VISION");
        startButton.setOnClickListener(v -> startVision());
        layout.addView(startButton);

        battleStartButton = new Button(this);
        battleStartButton.setText("BITKA POČELA - RESET ELIXIRA NA 5");
        battleStartButton.setOnClickListener(v -> {
            VisionStatus.startBattle();
            refreshStatus();
        });
        layout.addView(battleStartButton);

        stopButton = new Button(this);
        stopButton.setText("STOP ALL");
        stopButton.setOnClickListener(v -> stopVision());
        layout.addView(stopButton);

        TextView panelTitle = new TextView(this);
        panelTitle.setText("\\nPODACI O OBRADI");
        panelTitle.setTextSize(18);
        panelTitle.setPadding(0, 20, 0, 8);
        layout.addView(panelTitle);

        visionDetails = new TextView(this);
        visionDetails.setTextSize(14);
        visionDetails.setTextIsSelectable(true);
        visionDetails.setPadding(12, 12, 12, 12);

        ScrollView detailsScroll = new ScrollView(this);
        detailsScroll.addView(visionDetails);

        layout.addView(
            detailsScroll,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        );

        TextView info = new TextView(this);
        info.setText(
            "\nVision radi u pozadini dok je servis aktivan.\n" +
            "Možeš ga zaustaviti ovdje ili preko STOP akcije u obavijesti."
        );
        info.setTextSize(14);
        layout.addView(info);

        setContentView(layout);
        refreshStatus();
    }

    private void startVision() {
        if (ScreenCaptureService.isRunning()) {
            refreshStatus();
            return;
        }

        Intent permissionIntent =
            projectionManager.createScreenCaptureIntent();

        startActivityForResult(permissionIntent, CAPTURE_REQUEST);
    }

    private void stopVision() {
        BotRuntime.stopAll(this);
        status.setText("Status: SVE ZAUSTAVLJENO");
        refreshStatus();
    }

    private void refreshStatus() {
        boolean running = ScreenCaptureService.isRunning();

        if (status != null) {
            status.setText(
                running ? "Status: VISION AKTIVAN"
                        : "Status: VISION ISKLJUČEN"
            );
        }

        if (visionDetails != null) {
            visionDetails.setText(VisionStatus.getReport());
        }

        if (startButton != null) {
            startButton.setEnabled(!running);
        }

        if (stopButton != null) {
            stopButton.setEnabled(running);
        }

        if (battleStartButton != null) {
            battleStartButton.setEnabled(running);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        uiHandler.removeCallbacks(statusTicker);
        uiHandler.post(statusTicker);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(statusTicker);
        super.onPause();
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != CAPTURE_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            BotRuntime.stopAll(this);
            status.setText("Status: dozvola nije odobrena");
            refreshStatus();
            return;
        }

        BotRuntime.startSession();

        Intent service = new Intent(this, ScreenCaptureService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("projectionData", data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }

        status.setText("Status: pokretanje Visiona...");
        status.postDelayed(this::refreshStatus, 1000);
    }
}
