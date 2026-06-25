package com.milodon.vmc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.milodon.vmc.hardware.PaymentManager;

public class PaymentActivity extends Activity implements PaymentManager.PaymentListener {
    private static final String TAG = "PAYMENT_ACTIVITY";
    private String sku;
    private int amount;
    private Button btnCancel;
    private TextView tvAmount;
    private boolean isTransitioning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setupKioskMode();
        setContentView(R.layout.activity_payment);

        amount = getIntent().getIntExtra("AMOUNT", 0);
        sku = getIntent().getStringExtra("SKU");

        tvAmount = findViewById(R.id.tvPaymentAmount);
        btnCancel = findViewById(R.id.btnCancelPayment);

        if (tvAmount != null) {
            tvAmount.setText("Por favor, acerque su tarjeta\nMonto: $" + amount);
        }

        PaymentManager.getInstance().setListener(this);

        Log.d(TAG, "Solicitando inicio de flujo de cobro al hardware por SKU: " + sku);
        // CORRECCIÓN PUENTE: Envío conjunto de monto y SKU estructurado
        PaymentManager.getInstance().payment(String.valueOf(amount), sku);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                btnCancel.setEnabled(false);
                btnCancel.setText("Cancelando... Espere");
                if (tvAmount != null) {
                    tvAmount.setText("Abortando operación financiera...");
                }
                Log.w(TAG, "Solicitud manual de cancelación enviada.");
                PaymentManager.getInstance().cancel();
            });
        }
    }

    private void setupKioskMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setupKioskMode();
    }

    @Override
    public void onBackPressed() {
        Log.w(TAG, "Botón Atrás inhabilitado durante procesamiento MDB.");
    }

    @Override
    public void onPaymentSuccess() {
        runOnUiThread(() -> {
            if (isFinishing() || isTransitioning) return;
            isTransitioning = true;

            Intent dispenseIntent = new Intent(this, DispensingActivity.class);
            dispenseIntent.putExtra("SKU", sku);
            dispenseIntent.putExtra("AMOUNT", amount);
            startActivity(dispenseIntent);

            finish();
        });
    }

    @Override
    public void onPaymentFailed(String reason) {
        runOnUiThread(() -> {
            if (isFinishing() || isTransitioning) return;
            isTransitioning = true;

            Toast.makeText(this, "Operación terminada: " + reason, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        PaymentManager.getInstance().setListener(null);
        super.onDestroy();
    }
}