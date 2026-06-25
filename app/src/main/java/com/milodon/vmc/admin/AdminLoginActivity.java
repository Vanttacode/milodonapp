package com.milodon.vmc.admin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.milodon.vmc.R;

public class AdminLoginActivity extends Activity {

    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupKioskMode();
        setContentView(R.layout.activity_admin_login);

        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            if (failedAttempts >= MAX_ATTEMPTS) {
                Toast.makeText(this, "Bloqueado temporalmente. Espere...", Toast.LENGTH_SHORT).show();
                return;
            }

            String pass = etPassword.getText().toString();
            // [FIX: Contraseña protegida]
            if ("170410".equals(pass)) {
                failedAttempts = 0;
                startActivity(new Intent(this, AdminDashboardActivity.class));
                finish();
            } else {
                failedAttempts++;
                etPassword.setText("");
                Toast.makeText(this, "Acceso denegado. Intento: " + failedAttempts, Toast.LENGTH_SHORT).show();
            }
        });
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
}