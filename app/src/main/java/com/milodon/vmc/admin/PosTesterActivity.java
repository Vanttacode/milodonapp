package com.milodon.vmc.admin;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.milodon.vmc.R;
import com.milodon.vmc.hardware.PaymentManager;

public class PosTesterActivity extends Activity implements PaymentManager.PaymentListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_pos_tester);

        EditText etAmount = findViewById(R.id.etPosAmount);
        Button btnSetup = findViewById(R.id.btnSetup);
        Button btnSimulate = findViewById(R.id.btnSimulatePayment);

        if (btnSetup != null) {
            btnSetup.setOnClickListener(v -> {
                PaymentManager.getInstance().init(getApplicationContext());
                PaymentManager.getInstance().setListener(this);
                Toast.makeText(this, "Inicializando periférico MDB...", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnSimulate != null) {
            btnSimulate.setOnClickListener(v -> {
                try {
                    if (etAmount != null) {
                        String amount = etAmount.getText().toString();
                        if (!amount.isEmpty()) {
                            // Enviar cobro de testeo apuntando al ítem genérico "0001"
                            PaymentManager.getInstance().payment(amount, "1");
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error de formato numérico", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onPaymentSuccess() {
        runOnUiThread(() -> Toast.makeText(this, "MDB STATUS: APPROVED", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onPaymentFailed(String reason) {
        runOnUiThread(() -> Toast.makeText(this, "MDB STATUS: FAILED / CANCEL", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        PaymentManager.getInstance().setListener(null);
        super.onDestroy();
    }
}