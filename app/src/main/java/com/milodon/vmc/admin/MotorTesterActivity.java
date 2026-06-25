package com.milodon.vmc.admin;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.milodon.vmc.hardware.MotorManager;

public class MotorTesterActivity extends Activity implements MotorManager.MotorListener {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setupKioskMode();

        // CORRECCIÓN GRÁFICA: Uso de layouts lineales encadenados compatibles con API 22
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.BLACK);
        mainLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        for (int r = 1; r <= 6; r++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            rowLayout.setLayoutParams(rowParams);

            for (int c = 0; c <= 9; c++) {
                final Button btn = new Button(this);
                String displayLabel = r + String.valueOf(c);
                final String skuHardware = displayLabel + "0";

                btn.setText(displayLabel);
                btn.setTextSize(14);
                btn.setBackgroundColor(Color.DKGRAY);
                btn.setTextColor(Color.WHITE);

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                btnParams.setMargins(2, 2, 2, 2);
                btn.setLayoutParams(btnParams);

                btn.setOnClickListener(v -> handleMotorClick(btn, skuHardware));

                rowLayout.addView(btn);
            }
            mainLayout.addView(rowLayout);
        }
        setContentView(mainLayout);
    }

    private void handleMotorClick(Button btn, String sku) {
        btn.setEnabled(false);
        btn.setBackgroundColor(Color.YELLOW);
        btn.setTextColor(Color.BLACK);

        MotorManager.getInstance().rotateMotor(sku, this);

        uiHandler.postDelayed(() -> {
            btn.setEnabled(true);
            btn.setBackgroundColor(Color.DKGRAY);
            btn.setTextColor(Color.WHITE);
        }, 3000);
    }

    private void setupKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onMotorSuccess() {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> Toast.makeText(this, "Motor OK", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onMotorError(String reason) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> Toast.makeText(this, "ERROR: " + reason, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}