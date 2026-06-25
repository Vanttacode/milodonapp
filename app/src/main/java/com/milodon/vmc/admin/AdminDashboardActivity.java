package com.milodon.vmc.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.milodon.vmc.R;
import com.milodon.vmc.hardware.MotorManager;

public class AdminDashboardActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupKioskMode();
        setContentView(R.layout.activity_admin_dashboard);

        // Botón Probador de Motores: Con validación de hardware
        findViewById(R.id.btnMotorTester).setOnClickListener(v -> {
            startActivity(new Intent(this, MotorTesterActivity.class));
        });

        // Botón Probador POS: Acceso directo
        findViewById(R.id.btnPosTester).setOnClickListener(v ->
                startActivity(new Intent(this, PosTesterActivity.class)));

        // Botón Salir: Cierre seguro del panel
        findViewById(R.id.btnExit).setOnClickListener(v -> finish());
    }

    private void setupKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setupKioskMode();
    }

    @Override
    public void onBackPressed() {
        // Bloqueo total: Solo se sale por el botón Exit
    }
}