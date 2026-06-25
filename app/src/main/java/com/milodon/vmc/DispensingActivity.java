package com.milodon.vmc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import com.milodon.vmc.hardware.MotorManager;
import com.milodon.vmc.hardware.PaymentManager;

public class DispensingActivity extends Activity implements MotorManager.MotorListener {
    private static final String TAG = "DISPENSING_ACTIVITY";
    private TextView tvDispensingStatus;
    private String sku;
    private int amount;
    private boolean hasProcessedMdb = false;
    private SupabaseClient supabaseClient;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setupKioskMode();
        setContentView(R.layout.activity_dispensing);

        tvDispensingStatus = findViewById(R.id.tvDispensingStatus);
        sku = getIntent().getStringExtra("SKU");
        amount = getIntent().getIntExtra("AMOUNT", 0);
        supabaseClient = new SupabaseClient();

        if (sku == null || sku.trim().isEmpty()) {
            if (tvDispensingStatus != null) {
                tvDispensingStatus.setText("Error crítico: Producto no identificado.");
            }
            PaymentManager.getInstance().vendFailure();
            PaymentManager.getInstance().sessionComplete();
            abortAndExit(3000);
            return;
        }

        if (tvDispensingStatus != null) {
            tvDispensingStatus.setText("Preparando despacho...\nPor favor, no introduzca la mano.");
        }

        MotorManager.getInstance().rotateMotor(sku, this);
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
    public void onMotorSuccess() {
        runOnUiThread(() -> {
            if (isFinishing() || hasProcessedMdb) return;
            hasProcessedMdb = true;

            if (tvDispensingStatus != null) {
                tvDispensingStatus.setText("¡Producto entregado con éxito!\nMuchas gracias por su compra.");
            }

            // Confirmación de entrega al adaptador Waferstar con SKU indexado
            PaymentManager.getInstance().vendSuccess(sku);
            PaymentManager.getInstance().sessionComplete();

            supabaseClient.descontarStock(sku, new SupabaseClient.TransactionCallback() {
                @Override public void onSuccess() { Log.i(TAG, "Stock descontado en la nube."); }
                @Override public void onError(Exception e) { Log.e(TAG, "Falla stock en nube", e); }
            });

            supabaseClient.insertTransaction(amount, "SUCCESS", sku, new SupabaseClient.TransactionCallback() {
                @Override public void onSuccess() { Log.i(TAG, "Transacción grabada en Supabase."); }
                @Override public void onError(Exception e) { Log.e(TAG, "Falla transacción en nube", e); }
            });

            abortAndExit(4000);
        });
    }

    @Override
    public void onMotorError(String reason) {
        runOnUiThread(() -> {
            if (isFinishing() || hasProcessedMdb) return;
            hasProcessedMdb = true;

            if (tvDispensingStatus != null) {
                tvDispensingStatus.setText("Lo sentimos, ocurrió un problema al dispensar.\nTu dinero no ha sido cobrado.");
            }

            // Forzar reverso monetario al bus MDB
            PaymentManager.getInstance().vendFailure();
            PaymentManager.getInstance().sessionComplete();

            supabaseClient.insertTransaction(amount, "MOTOR_ERROR", sku, new SupabaseClient.TransactionCallback() {
                @Override public void onSuccess() { Log.i(TAG, "Alerta de motor registrada en base de datos."); }
                @Override public void onError(Exception e) { Log.e(TAG, "Falla registro alerta nube", e); }
            });

            abortAndExit(6000);
        });
    }

    private void abortAndExit(long delayMs) {
        handler.postDelayed(this::finish, delayMs);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}