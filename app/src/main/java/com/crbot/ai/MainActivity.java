package com.crbot.ai;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crbot.ai.automation.ScreenCaptureService;

public class MainActivity extends Activity {

    private static final int CAPTURE_REQUEST = 1001;
    private MediaProjectionManager projectionManager;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectionManager =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 40, 32, 32);

        TextView title = new TextView(this);
        title.setText("CrBot AI 3.0");
        title.setTextSize(28);
        layout.addView(title);

        status = new TextView(this);
        status.setText("Status: spreman");
        status.setTextSize(16);
        layout.addView(status);

        Button accessibility = new Button(this);
        accessibility.setText("1. Uključi Accessibility");
        accessibility.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );
        layout.addView(accessibility);

        Button capture = new Button(this);
        capture.setText("2. Pokreni Vision");
        capture.setOnClickListener(v ->
            startActivityForResult(
                projectionManager.createScreenCaptureIntent(),
                CAPTURE_REQUEST
            )
        );
        layout.addView(capture);

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAPTURE_REQUEST && resultCode == RESULT_OK) {
            Intent service = new Intent(this, ScreenCaptureService.class);
            service.putExtra("resultCode", resultCode);
            service.putExtra("projectionData", data);

            startForegroundService(service);
            status.setText("Status: snimanje ekrana pokrenuto");
        }
    }
}
